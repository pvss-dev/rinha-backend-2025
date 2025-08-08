package br.com.pvss.rinhabackend2025.repository;

import br.com.pvss.rinhabackend2025.model.ReceivedRequest;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface ReceivedRequestRepo extends ReactiveMongoRepository<ReceivedRequest, String> {
}