package com.gabon.common.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 前端视频状态枚举（客户端视角）
 * Client-facing Video Status Enum.
 *
 * <p>
 * Clients do not care about internal transcode/review pipeline stages.
 * This enum collapses the internal {@link VideoStatusEnum} into three
 * user-visible states and provides a helper to resolve the actual DB
 * status codes that each client state maps to.
 * </p>
 *
 * <pre>
 * Client code | Label    | Internal codes (VideoStatusEnum)
 * ------------|----------|------------------------------------
 *      4      | 已上架   | 4  (APPROVED)
 *      3      | 审核中   | 1, 2, 3  (PENDING_TRANSCODE + TRANSCODING + PENDING_REVIEW)
 *      5      | 未通过   | 0, 5  (FAILED + REJECTED)
 * </pre>
 */
@Getter
@AllArgsConstructor
@Schema(description = "前端视频状态枚举 | Client-facing Video Status Enum")
public enum VideoClientStatusEnum {

    @Schema(description = "4-已上架 (Published) → internal: APPROVED(4)")
    PUBLISHED(4, "已上架", Arrays.asList(4)),

    @Schema(description = "3-审核中 (Under Review) → internal: PENDING_TRANSCODE(1), TRANSCODING(2), PENDING_REVIEW(3)")
    UNDER_REVIEW(3, "审核中", Arrays.asList(1, 2, 3)),

    @Schema(description = "5-未通过 (Not Passed) → internal: FAILED(0), REJECTED(5)")
    NOT_PASSED(5, "未通过", Arrays.asList(0, 5));

    /** The code the client sends in its request. */
    private final Integer code;

    private final String message;

    /**
     * The list of internal {@link VideoStatusEnum} codes that this client
     * status represents. Use this for DB queries, e.g.:
     * 
     * <pre>
     * wrapper.in(Video::getStatus, clientStatus.getInternalCodes());
     * </pre>
     */
    private final List<Integer> internalCodes;

    /**
     * Resolve a {@link VideoClientStatusEnum} from the client-supplied code.
     *
     * @param code the client status code (3, 4, or 5)
     * @return the matching enum constant, or {@code null} if not found
     */
    public static VideoClientStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (VideoClientStatusEnum s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return null;
    }
}
