package com.securebank.bankingcore.application.transfer;

import com.securebank.bankingcore.api.CreateTransferRequest;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.security.AuthenticatedUser;
import com.securebank.common.web.Masking;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Entry point of POST /transfers. Deliberately <b>not</b> transactional: it orchestrates
 * <ol>
 *   <li>stateless validation (400s, never persisted) and request hashing;</li>
 *   <li>the idempotent fast path (stored response → replay, different payload → 409);</li>
 *   <li>the money transaction ({@link TransferExecutor});</li>
 *   <li>on a business rejection, which rolled the money transaction back completely, a second transaction
 *       that persists the REJECTED attempt ({@link TransferRejectionRecorder}).</li>
 * </ol>
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferRequestValidator validator;
    private final RequestHasher hasher;
    private final IdempotencyService idempotency;
    private final TransferExecutor executor;
    private final TransferRejectionRecorder rejections;
    private final TransferMetrics metrics;

    public TransferService(TransferRequestValidator validator, RequestHasher hasher, IdempotencyService idempotency,
                           TransferExecutor executor, TransferRejectionRecorder rejections, TransferMetrics metrics) {
        this.validator = validator;
        this.hasher = hasher;
        this.idempotency = idempotency;
        this.executor = executor;
        this.rejections = rejections;
        this.metrics = metrics;
    }

    public TransferResult submit(AuthenticatedUser user, String rawIdempotencyKey, CreateTransferRequest request) {
        Timer.Sample sample = metrics.start();
        try {
            String key = validator.validateIdempotencyKey(rawIdempotencyKey);
            TransferCommand command = validator.validate(request);
            String hash = hasher.hash(command);

            Optional<TransferResult> stored = idempotency.findReplay(user.userId(), key, hash);
            if (stored.isPresent()) {
                metrics.replayed();
                log.info("Transfer replayed from idempotency record tx={}", stored.get().transactionId());
                return stored.get();
            }

            TransferResult result;
            try {
                result = executor.execute(user, key, hash, command);
            } catch (TransferRejectedException rejection) {
                result = rejections.record(user, key, hash, command, rejection);
                if (result.replayed()) {
                    metrics.replayed();
                } else {
                    metrics.failure(rejection.code().name());
                    log.info("Transfer rejected code={} tx={} from={} to={} amount={}", rejection.code(),
                            result.transactionId(), Masking.accountNumber(command.sourceAccountNumber()),
                            Masking.accountNumber(command.destinationAccountNumber()), command.amount());
                }
                return result;
            }
            if (result.replayed()) {
                metrics.replayed();
            } else {
                metrics.success();
                log.info("Transfer completed tx={} from={} to={} amount={} {}", result.transactionId(),
                        Masking.accountNumber(command.sourceAccountNumber()),
                        Masking.accountNumber(command.destinationAccountNumber()), command.amount(),
                        command.currency());
            }
            return result;
        } catch (ApiException e) {
            metrics.failure(e.code().name());
            throw e;
        } catch (RuntimeException e) {
            metrics.failure(ErrorCode.INTERNAL_ERROR.name());
            throw e;
        } finally {
            metrics.stop(sample);
        }
    }
}
