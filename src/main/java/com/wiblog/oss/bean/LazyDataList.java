package com.wiblog.oss.bean;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 懒加载列表结果。
 *
 * @author panwm
 * @since 2024/12/6 15:51
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "懒加载分页结果")
public class LazyDataList<T> {

    @Schema(description = "单次查询允许返回的最大记录数")
    private long maxKeys;

    @Schema(description = "下一页查询游标")
    private String continuationToken;

    @Schema(description = "当前批次记录")
    private List<T> records;

    public void addAll(List<T> records) {
        if (this.records == null) {
            this.records = new ArrayList<>(records);
        } else {
            this.records.addAll(records);
        }
    }

    public void add(T item) {
        if (this.records == null) {
            this.records = new ArrayList<>();
        }
        this.records.add(item);
    }
}
