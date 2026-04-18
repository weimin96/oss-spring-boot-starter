package com.wiblog.oss.contract;

/**
 * `javax` 命名空间控制器契约测试入口。
 *
 * <p>Boot2 的控制器测试通过该入口复用公共控制器契约，
 * 这样版本模块本地只需要提供公开类型构造方式。</p>
 *
 * @param <T> 控制器公开类型
 * @author panwm
 */
public abstract class AbstractJavaxOssControllerContractTest<T> extends AbstractOssControllerContractTest<T> {
}
