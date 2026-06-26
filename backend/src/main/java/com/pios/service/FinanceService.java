package com.pios.service;

import com.pios.domain.*;
import com.pios.dto.finance.*;
import com.pios.repository.*;
import com.pios.service.finance.FinanceExcelParser;
import com.pios.service.finance.ParsedFinanceRow;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinanceService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter CYCLE_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");

    private final FinanceExcelParser excelParser;
    private final FinanceCycleRepository cycleRepo;
    private final FinanceTransactionRepository transactionRepo;
    private final RecurringBillTemplateRepository templateRepo;
    private final RecurringBillTemplateVersionRepository versionRepo;
    private final RecurringBillTemplateItemRepository itemRepo;

    public List<FinanceCycleDto> getCycles(Long userId) {
        return cycleRepo.findByUserIdOrderByStartsAtDesc(userId).stream().map(this::toCycleDto).toList();
    }

    public List<FinanceTransactionDto> getTransactions(Long userId, Long cycleId) {
        List<FinanceTransaction> transactions = cycleId == null
                ? transactionRepo.findByUserIdOrderByTransactionAtDesc(userId)
                : transactionRepo.findByUserIdAndCycleIdOrderByTransactionAtDesc(userId, cycleId);
        return transactions.stream().map(this::toTransactionDto).toList();
    }

    public FinanceImportPreviewResponse previewImport(Long userId, MultipartFile file) {
        List<ParsedFinanceRow> parsedRows = excelParser.parse(file);
        Map<Instant, CycleAnchor> anchors = detectAnchors(userId, parsedRows);
        Set<String> existingFingerprints = transactionRepo.findByUserIdAndSourceFingerprintIn(
                        userId,
                        parsedRows.stream().map(ParsedFinanceRow::sourceFingerprint).toList()
                ).stream()
                .map(FinanceTransaction::getSourceFingerprint)
                .collect(Collectors.toSet());

        List<FinanceImportRowDto> rows = parsedRows.stream()
                .map(row -> toPreviewRow(userId, row, anchors, existingFingerprints))
                .toList();

        return FinanceImportPreviewResponse.builder()
                .importSessionId(UUID.randomUUID().toString())
                .totalRows(rows.size())
                .newRows((int) rows.stream().filter(r -> "NEW".equals(r.getStatus())).count())
                .duplicateRows((int) rows.stream().filter(r -> "DUPLICATE".equals(r.getStatus())).count())
                .reviewRows((int) rows.stream().filter(r -> "NEEDS_REVIEW".equals(r.getStatus())).count())
                .rows(rows)
                .build();
    }

    @Transactional
    public FinanceImportConfirmResponse confirmImport(Long userId, FinanceImportConfirmRequest request) {
        if (request.getDecisions() == null) {
            return FinanceImportConfirmResponse.builder().created(0).skipped(0).transactions(List.of()).build();
        }
        int skipped = 0;
        List<FinanceTransactionDto> created = new ArrayList<>();
        for (FinanceImportDecisionDto decision : request.getDecisions()) {
            FinanceImportRowDto row = decision.getRow();
            String action = decision.getAction() == null ? "" : decision.getAction();
            if (row == null || !"create".equalsIgnoreCase(action)) {
                skipped++;
                continue;
            }
            if (transactionRepo.findByUserIdAndSourceFingerprint(userId, row.getSourceFingerprint()).isPresent()) {
                skipped++;
                continue;
            }
            try {
                FinanceCycle cycle = ensureCycle(userId, row);
                FinanceTransaction transaction = transactionRepo.save(FinanceTransaction.builder()
                        .user(User.builder().id(userId).build())
                        .cycle(cycle)
                        .transactionAt(row.getTransactionAt())
                        .transactionDate(row.getTransactionDate())
                        .asset(row.getAsset())
                        .category(row.getCategory())
                        .subcategory(row.getSubcategory())
                        .description(row.getDescription())
                        .amount(row.getAmount())
                        .flowType(row.getFlowType())
                        .memo(row.getMemo())
                        .currency(row.getCurrency() == null || row.getCurrency().isBlank() ? "KRW" : row.getCurrency())
                        .sourceFingerprint(row.getSourceFingerprint())
                        .sourceRow(row.getSourceRow() == null ? Map.of() : row.getSourceRow())
                        .cashflowIncluded(row.isCashflowIncluded())
                        .spendingIncluded(row.isSpendingIncluded())
                        .paymentMethod(row.getPaymentMethod())
                        .build());
                created.add(toTransactionDto(transaction));
            } catch (DataIntegrityViolationException e) {
                skipped++;
            }
        }
        closeCycleRanges(userId);
        return FinanceImportConfirmResponse.builder()
                .created(created.size())
                .skipped(skipped)
                .transactions(created)
                .build();
    }

    public List<RecurringBillDto> getRecurringBills(Long userId) {
        return templateRepo.findByUserIdOrderByNameAsc(userId).stream().map(this::toRecurringBillDto).toList();
    }

    @Transactional
    public RecurringBillDto createRecurringBill(Long userId, RecurringBillDto dto) {
        RecurringBillTemplate template = templateRepo.save(RecurringBillTemplate.builder()
                .user(User.builder().id(userId).build())
                .name(dto.getName())
                .provider(dto.getProvider())
                .category(dto.getCategory())
                .memo(dto.getMemo())
                .active(true)
                .build());
        return toRecurringBillDto(template);
    }

    @Transactional
    public RecurringBillVersionDto createRecurringBillVersion(Long userId, Long templateId, RecurringBillVersionDto dto) {
        RecurringBillTemplate template = findTemplate(userId, templateId);
        int nextVersion = versionRepo.findFirstByTemplateIdOrderByVersionDesc(templateId)
                .map(v -> v.getVersion() + 1)
                .orElse(1);
        FinanceCycle cycle = null;
        if (dto.getEffectiveCycleId() != null) {
            cycle = cycleRepo.findById(dto.getEffectiveCycleId())
                    .filter(c -> c.getUser().getId().equals(userId))
                    .orElseThrow(() -> new IllegalArgumentException("Finance cycle not found"));
        }
        BigDecimal expected = dto.getExpectedAmount();
        if (expected == null && dto.getItems() != null) {
            expected = dto.getItems().stream()
                    .map(RecurringBillItemDto::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (expected == null) expected = BigDecimal.ZERO;

        RecurringBillTemplateVersion version = versionRepo.save(RecurringBillTemplateVersion.builder()
                .template(template)
                .effectiveCycle(cycle)
                .version(nextVersion)
                .expectedAmount(expected)
                .active(true)
                .build());

        if (dto.getItems() != null) {
            int order = 0;
            for (RecurringBillItemDto item : dto.getItems()) {
                if (item.getItemName() == null || item.getItemName().isBlank()) continue;
                itemRepo.save(RecurringBillTemplateItem.builder()
                        .version(version)
                        .itemName(item.getItemName())
                        .amount(item.getAmount() == null ? BigDecimal.ZERO : item.getAmount())
                        .itemType(item.getItemType() == null || item.getItemType().isBlank() ? "BASE" : item.getItemType())
                        .sortOrder(item.getSortOrder() == null ? order : item.getSortOrder())
                        .build());
                order++;
            }
        }
        return toVersionDto(version);
    }

    @Transactional
    public void deleteRecurringBill(Long userId, Long templateId) {
        RecurringBillTemplate template = findTemplate(userId, templateId);
        templateRepo.delete(template);
    }

    private FinanceImportRowDto toPreviewRow(Long userId, ParsedFinanceRow row, Map<Instant, CycleAnchor> anchors,
                                             Set<String> existingFingerprints) {
        CycleAnchor anchor = resolveAnchor(userId, row, anchors);
        String status = "NEW";
        Long matchedId = null;
        if (existingFingerprints.contains(row.sourceFingerprint())) {
            status = "DUPLICATE";
            matchedId = transactionRepo.findByUserIdAndSourceFingerprint(userId, row.sourceFingerprint())
                    .map(FinanceTransaction::getId)
                    .orElse(null);
        } else if (!transactionRepo.findByUserIdAndTransactionDateAndAmountAndFlowType(
                userId, row.transactionDate(), row.amount(), row.flowType()).isEmpty()) {
            status = "NEEDS_REVIEW";
        }
        return FinanceImportRowDto.builder()
                .rowId(UUID.randomUUID().toString())
                .rowNumber(row.rowNumber())
                .status(status)
                .matchedTransactionId(matchedId)
                .cycleId(anchor.cycleId())
                .cycleLabel(anchor.label())
                .cycleStartsAt(anchor.startsAt())
                .cycleSalaryDate(anchor.salaryDate())
                .transactionAt(row.transactionAt())
                .transactionDate(row.transactionDate())
                .asset(row.asset())
                .category(row.category())
                .subcategory(row.subcategory())
                .description(row.description())
                .amount(row.amount())
                .flowType(row.flowType())
                .memo(row.memo())
                .currency(row.currency())
                .sourceFingerprint(row.sourceFingerprint())
                .cashflowIncluded(row.cashflowIncluded())
                .spendingIncluded(row.spendingIncluded())
                .paymentMethod(row.paymentMethod())
                .sourceRow(row.sourceRow())
                .build();
    }

    private Map<Instant, CycleAnchor> detectAnchors(Long userId, List<ParsedFinanceRow> rows) {
        Map<Instant, CycleAnchor> anchors = new TreeMap<>();
        for (FinanceCycle cycle : cycleRepo.findByUserIdOrderByStartsAtDesc(userId)) {
            anchors.put(cycle.getStartsAt(), new CycleAnchor(cycle.getId(), cycle.getLabel(), cycle.getStartsAt(), cycle.getSalaryDate()));
        }
        for (ParsedFinanceRow row : rows) {
            if (isSalary(row)) {
                String label = row.transactionAt().atZone(SEOUL).format(CYCLE_LABEL) + " Salary Cycle";
                anchors.putIfAbsent(row.transactionAt(), new CycleAnchor(null, label, row.transactionAt(), row.transactionDate()));
            }
        }
        if (anchors.isEmpty() && !rows.isEmpty()) {
            ParsedFinanceRow first = rows.stream().min(Comparator.comparing(ParsedFinanceRow::transactionAt)).orElseThrow();
            Instant start = first.transactionDate().atStartOfDay(SEOUL).toInstant();
            anchors.put(start, new CycleAnchor(null, first.transactionDate().format(CYCLE_LABEL) + " Salary Cycle", start, first.transactionDate()));
        }
        return anchors;
    }

    private CycleAnchor resolveAnchor(Long userId, ParsedFinanceRow row, Map<Instant, CycleAnchor> anchors) {
        Instant target = row.transactionAt();
        if (row.memo() != null && row.memo().contains("급여 전")) {
            target = row.transactionDate().atStartOfDay(SEOUL).minusNanos(1).toInstant();
        }
        if (row.memo() != null && row.memo().contains("급여 후")) {
            Optional<Instant> sameDaySalary = anchors.keySet().stream()
                    .filter(a -> a.atZone(SEOUL).toLocalDate().equals(row.transactionDate()))
                    .findFirst();
            if (sameDaySalary.isPresent()) target = sameDaySalary.get();
        }
        Instant selected = null;
        for (Instant anchor : anchors.keySet()) {
            if (!anchor.isAfter(target)) selected = anchor;
        }
        if (selected == null) {
            return cycleRepo.findFirstByUserIdAndStartsAtLessThanEqualOrderByStartsAtDesc(userId, target)
                    .map(c -> new CycleAnchor(c.getId(), c.getLabel(), c.getStartsAt(), c.getSalaryDate()))
                    .orElseGet(() -> anchors.values().iterator().next());
        }
        return anchors.get(selected);
    }

    private FinanceCycle ensureCycle(Long userId, FinanceImportRowDto row) {
        if (row.getCycleId() != null) {
            return cycleRepo.findById(row.getCycleId())
                    .filter(c -> c.getUser().getId().equals(userId))
                    .orElse(null);
        }
        Instant startsAt = row.getCycleStartsAt() == null ? row.getTransactionDate().atStartOfDay(SEOUL).toInstant() : row.getCycleStartsAt();
        return cycleRepo.findByUserIdAndStartsAt(userId, startsAt)
                .orElseGet(() -> cycleRepo.save(FinanceCycle.builder()
                        .user(User.builder().id(userId).build())
                        .label(row.getCycleLabel() == null ? row.getTransactionDate().format(CYCLE_LABEL) + " Salary Cycle" : row.getCycleLabel())
                        .salaryDate(row.getCycleSalaryDate() == null ? row.getTransactionDate() : row.getCycleSalaryDate())
                        .startsAt(startsAt)
                        .status("OPEN")
                        .build()));
    }

    private void closeCycleRanges(Long userId) {
        List<FinanceCycle> cycles = new ArrayList<>(cycleRepo.findByUserIdOrderByStartsAtDesc(userId));
        cycles.sort(Comparator.comparing(FinanceCycle::getStartsAt));
        for (int i = 0; i < cycles.size(); i++) {
            FinanceCycle cycle = cycles.get(i);
            cycle.setEndsAt(i + 1 < cycles.size() ? cycles.get(i + 1).getStartsAt().minusMillis(1) : null);
        }
        cycleRepo.saveAll(cycles);
    }

    private boolean isSalary(ParsedFinanceRow row) {
        if (!"수입".equals(row.flowType())) return false;
        String text = String.join(" ", safe(row.category()), safe(row.subcategory()), safe(row.description()));
        return text.contains("월급") || text.toLowerCase(Locale.ROOT).contains("salary");
    }

    private RecurringBillTemplate findTemplate(Long userId, Long templateId) {
        return templateRepo.findById(templateId)
                .filter(t -> t.getUser().getId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Recurring bill not found"));
    }

    private FinanceCycleDto toCycleDto(FinanceCycle c) {
        return FinanceCycleDto.builder()
                .id(c.getId())
                .label(c.getLabel())
                .salaryDate(c.getSalaryDate())
                .startsAt(c.getStartsAt())
                .endsAt(c.getEndsAt())
                .status(c.getStatus())
                .build();
    }

    private FinanceTransactionDto toTransactionDto(FinanceTransaction t) {
        return FinanceTransactionDto.builder()
                .id(t.getId())
                .cycleId(t.getCycle() != null ? t.getCycle().getId() : null)
                .linkedTemplateVersionId(t.getLinkedTemplateVersion() != null ? t.getLinkedTemplateVersion().getId() : null)
                .transactionAt(t.getTransactionAt())
                .transactionDate(t.getTransactionDate())
                .asset(t.getAsset())
                .category(t.getCategory())
                .subcategory(t.getSubcategory())
                .description(t.getDescription())
                .amount(t.getAmount())
                .flowType(t.getFlowType())
                .memo(t.getMemo())
                .currency(t.getCurrency())
                .sourceFingerprint(t.getSourceFingerprint())
                .sourceRow(t.getSourceRow())
                .cashflowIncluded(t.isCashflowIncluded())
                .spendingIncluded(t.isSpendingIncluded())
                .paymentMethod(t.getPaymentMethod())
                .build();
    }

    private RecurringBillDto toRecurringBillDto(RecurringBillTemplate template) {
        return RecurringBillDto.builder()
                .id(template.getId())
                .name(template.getName())
                .provider(template.getProvider())
                .category(template.getCategory())
                .memo(template.getMemo())
                .active(template.isActive())
                .versions(versionRepo.findByTemplateIdOrderByVersionDesc(template.getId()).stream().map(this::toVersionDto).toList())
                .build();
    }

    private RecurringBillVersionDto toVersionDto(RecurringBillTemplateVersion version) {
        return RecurringBillVersionDto.builder()
                .id(version.getId())
                .templateId(version.getTemplate().getId())
                .effectiveCycleId(version.getEffectiveCycle() != null ? version.getEffectiveCycle().getId() : null)
                .version(version.getVersion())
                .expectedAmount(version.getExpectedAmount())
                .active(version.isActive())
                .items(itemRepo.findByVersionIdOrderBySortOrderAsc(version.getId()).stream().map(this::toItemDto).toList())
                .build();
    }

    private RecurringBillItemDto toItemDto(RecurringBillTemplateItem item) {
        return RecurringBillItemDto.builder()
                .id(item.getId())
                .itemName(item.getItemName())
                .amount(item.getAmount())
                .itemType(item.getItemType())
                .sortOrder(item.getSortOrder())
                .build();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record CycleAnchor(Long cycleId, String label, Instant startsAt, LocalDate salaryDate) {
    }
}
