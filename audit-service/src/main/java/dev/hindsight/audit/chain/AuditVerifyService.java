package dev.hindsight.audit.chain;

import dev.hindsight.common.audit.AuditChainHash;
import dev.hindsight.common.audit.AuditGenesis;
import dev.hindsight.audit.persistence.AuditLogRecord;
import dev.hindsight.audit.persistence.AuditLogRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditVerifyService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AuditLogRepository auditLogRepository;
    private final String chainId;

    public AuditVerifyService(
            AuditLogRepository auditLogRepository, @Value("${hindsight.audit.chain-id:main}") String chainId) {
        this.auditLogRepository = auditLogRepository;
        this.chainId = chainId;
    }

    public VerifyResult verify(long from, long to) {
        List<AuditLogRecord> records = auditLogRepository.findRange(chainId, from, to);
        if (records.isEmpty()) {
            return VerifyResult.ok(from, to);
        }
        String expectedPrev = from == 1
                ? AuditGenesis.PREV_HASH
                : auditLogRepository
                        .findByChainAndSeq(chainId, from - 1)
                        .map(AuditLogRecord::hash)
                        .orElse(null);
        if (from > 1 && expectedPrev == null) {
            return VerifyResult.gap(from);
        }
        long expectedSeq = from;
        for (AuditLogRecord record : records) {
            if (record.seq() != expectedSeq) {
                return VerifyResult.gap(expectedSeq);
            }
            if (!record.prevHash().equals(expectedPrev)) {
                return VerifyResult.prevHashMismatch(record.seq());
            }
            String recomputed = AuditChainHash.compute(record.prevHash(), JSON.readTree(record.payloadJson()));
            if (!recomputed.equals(record.hash())) {
                return VerifyResult.hashMismatch(record.seq());
            }
            expectedPrev = record.hash();
            expectedSeq++;
        }
        return VerifyResult.ok(from, to);
    }

    public record VerifyResult(String status, Long brokenSeq, String reason, long from, long to) {

        static VerifyResult ok(long from, long to) {
            return new VerifyResult("OK", null, null, from, to);
        }

        static VerifyResult hashMismatch(long seq) {
            return new VerifyResult("BROKEN", seq, "HASH_MISMATCH", seq, seq);
        }

        static VerifyResult prevHashMismatch(long seq) {
            return new VerifyResult("BROKEN", seq, "PREV_HASH_MISMATCH", seq, seq);
        }

        static VerifyResult gap(long seq) {
            return new VerifyResult("BROKEN", seq, "GAP", seq, seq);
        }
    }
}
