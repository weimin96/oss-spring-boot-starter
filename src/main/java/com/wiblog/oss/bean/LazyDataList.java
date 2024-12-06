package com.wiblog.oss.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
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

    public void addAll(List<T> records) {
        if (this.records == null) {
            this.records = new ArrayList<>(records);
        } else {
            this.records.addAll(records);
        }
    }
}
