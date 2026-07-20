package ru.eltc.deliverymonitor.api.workstream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.eltc.deliverymonitor.domain.workstream.WorkstreamRepository;
import ru.eltc.deliverymonitor.domain.workstream_type.WorkstreamTypeEntity;
import ru.eltc.deliverymonitor.domain.workstream_type.WorkstreamTypeRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only aggregate for Dashboard "Projects" progress bars: share of workstreams
 * in {@code merged} per active Workstream Type. Not Release Health (no fixVersion).
 */
@Service
@Transactional(readOnly = true)
public class WorkstreamProgressQueryService {

    private final WorkstreamTypeRepository workstreamTypeRepository;
    private final WorkstreamRepository workstreamRepository;

    public WorkstreamProgressQueryService(
            WorkstreamTypeRepository workstreamTypeRepository,
            WorkstreamRepository workstreamRepository) {
        this.workstreamTypeRepository = workstreamTypeRepository;
        this.workstreamRepository = workstreamRepository;
    }

    public WorkstreamProgressResponse progress() {
        Map<String, long[]> countsByCode = new HashMap<>();
        for (Object[] row : workstreamRepository.countTotalsAndMergedByType()) {
            String code = (String) row[0];
            long total = ((Number) row[1]).longValue();
            long merged = ((Number) row[2]).longValue();
            countsByCode.put(code, new long[] {total, merged});
        }

        List<WorkstreamProgressResponse.Item> items = workstreamTypeRepository
                .findAllByActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(type -> toItem(type, countsByCode.getOrDefault(type.getCode(), new long[] {0L, 0L})))
                .toList();
        return new WorkstreamProgressResponse(items);
    }

    private static WorkstreamProgressResponse.Item toItem(WorkstreamTypeEntity type, long[] counts) {
        long total = counts[0];
        long merged = counts[1];
        int percent = total == 0 ? 0 : (int) Math.round(100.0 * merged / total);
        return new WorkstreamProgressResponse.Item(
                new WorkstreamProgressResponse.WorkstreamTypeRef(type.getCode(), type.getDisplayName()),
                total,
                merged,
                percent);
    }
}
