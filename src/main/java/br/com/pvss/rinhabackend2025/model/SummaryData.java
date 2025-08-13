package br.com.pvss.rinhabackend2025.model;

import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("summary_data")
public class SummaryData {

    @Id
    private ProcessorType processor;
    private final long totalRequests;
    private final long totalAmountCents;

    public SummaryData(long totalRequests, long totalAmountCents) {
        this.totalRequests = totalRequests;
        this.totalAmountCents = totalAmountCents;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public long getTotalAmountCents() {
        return totalAmountCents;
    }
}