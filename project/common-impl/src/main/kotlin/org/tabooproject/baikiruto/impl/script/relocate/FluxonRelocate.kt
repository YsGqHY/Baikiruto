package org.tabooproject.baikiruto.impl.script.relocate

/**
 * 标记需要区分自建库和中心库的类
 * 缺点：无法调用被标记 @FluxonRelocate 的类, 因为在中心库存在的情况下类被转译了
 * 使用时请不要调用这些标记了 @FluxonRelocate 的类
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class FluxonRelocate