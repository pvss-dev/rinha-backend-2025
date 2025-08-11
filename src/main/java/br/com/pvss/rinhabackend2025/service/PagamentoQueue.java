package br.com.pvss.rinhabackend2025.service;

import br.com.pvss.rinhabackend2025.dto.ProcessorPaymentRequest;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class PagamentoQueue {

    private final BlockingQueue<ProcessorPaymentRequest> queue = new LinkedBlockingQueue<>();

    public void add(ProcessorPaymentRequest request) {
        queue.add(request);
    }

    public ProcessorPaymentRequest take() throws InterruptedException {
        return queue.take();
    }
}