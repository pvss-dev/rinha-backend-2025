package br.com.pvss.rinhabackend2025.model;

import br.com.pvss.rinhabackend2025.dto.ProcessorType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("summary_data")
public class SummaryData {

    @Id
    private ProcessorType processor;
    private long totalRequests;
    private long totalAmountCents;

    public ProcessorType getProcessor() {
        return processor;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public long getTotalAmountCents() {
        return totalAmountCents;
    }

    public void setProcessor(ProcessorType processor) {
        this.processor = processor;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public void setTotalAmountCents(long totalAmountCents) {
        this.totalAmountCents = totalAmountCents;
    }
}