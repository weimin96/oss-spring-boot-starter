package com.wiblog.oss.bean;

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
public class LazyDataList<T> {
    private long maxKeys;
    private String continuationToken;
    private List<T> records;

    /**
     * 追加一批记录。
     *
     * <p>懒加载分页结果允许多次累积内容，因此这里在 `records` 尚未初始化时会先创建列表，
     * 避免调用方在组装分页结果时重复做空值判断。</p>
     *
     * @param records 本次需要追加的记录集合
     */
    public void addAll(List<T> records) {
        if (this.records == null) {
            this.records = new ArrayList<>(records);
        } else {
            this.records.addAll(records);
        }
    }

    /**
     * 追加单条记录。
     *
     * <p>该方法与 {@link #addAll(List)} 保持同样的空列表初始化语义，
     * 让分页构建逻辑可以按“遇到一条加一条”的方式逐步组装结果。</p>
     *
     * @param item 需要追加的单条记录
     */
    public void add(T item) {
        if (this.records == null) {
            this.records = new ArrayList<>();
        }
        this.records.add(item);
    }
}


