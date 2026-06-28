UPDATE finance_transactions
SET spending_included = false,
    spending_amount = 0
WHERE flow_type = '수입'
   OR flow_type = '이체지출';

UPDATE finance_transactions
SET spending_included = true,
    spending_amount = amount
WHERE flow_type <> '수입'
  AND flow_type <> '이체지출';
