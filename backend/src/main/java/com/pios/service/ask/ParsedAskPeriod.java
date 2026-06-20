package com.pios.service.ask;

import com.pios.dto.AskPeriod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedAskPeriod {
    private AskPeriod period;
    private boolean explicit;
}
