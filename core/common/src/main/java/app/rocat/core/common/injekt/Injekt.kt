package app.rocat.core.common.injekt

import kotlin.reflect.KClass

/**
 * A minimal, dependency-free re-implementation of the `uy.kohesive.injekt` API shape
 * used by mihon (`Injekt.get`, `injectLazy`, `InjektModule`, `registerInjectables`,
 * `addSingleton`, `addSingletonFactory`). Keeping the same method names means the app
 * code is written identically to mihon while avoiding a brittle legacy artifact.
 */
object Injekt {

    private val lock = Any()

    /** Maps a runtime type name to a factory that produces its value. */
    @PublishedApi
    internal val store: MutableMap<String, () -> Any?> = HashMap()

    /** Resolves a registered value by its runtime type. */
    inline fun <reified T> get(): T = resolve(T::class) as T

    /** Resolves a registered value, or null when absent. */
    inline fun <reified T> getOrNull(): T? = resolve(T::class) as T?

    /** Lazy delegate resolving on first access. */
    inline fun <reified T> injectLazy(): Lazy<T> = lazy { get<T>() }

    @PublishedApi
    internal fun resolve(kClass: KClass<*>): Any? {
        val name = kClass.qualifiedName ?: kClass.simpleName ?: "Any"
        return synchronized(lock) { store[name]?.invoke() }
    }

    /** Applies [module]'s registrations to the global store. */
    fun importModule(module: InjektModule) {
        val registrar = Registrar()
        module.registerWith(registrar)
        synchronized(lock) { store.putAll(registrar.wired) }
    }

    /** Replaces the store with [wired] entries (used by tests / app startup). */
    fun reset(entries: Map<String, () -> Any?>) {
        synchronized(lock) {
            store.clear()
            store.putAll(entries)
        }
    }
}

/**
 * Receiver for registration, mirroring injekt's `InjektRegistrar`.
 */
class Registrar internal constructor() {
    @PublishedApi
    internal val wired: MutableMap<String, () -> Any?> = HashMap()

    inline fun <reified T> addSingleton(instance: T) {
        wired[typeName<T>()] = { instance }
    }

    inline fun <reified T> addSingletonFactory(noinline factory: () -> T) {
        wired[typeName<T>()] = factory
    }

    inline fun <reified T> addFactory(noinline factory: () -> T) {
        wired[typeName<T>()] = factory
    }

    /** Alternatively add a raw instance keyed by its class. */
    fun add(instance: Any) {
        wired[instance.javaClass.name] = { instance }
    }
}

/** Module contract mirroring `uy.kohesive.injekt.api.InjektModule`. */
interface InjektModule {
    fun registerWith(registrar: Registrar) {
        registerInjectables(registrar)
    }

    fun registerInjectables(registrar: Registrar)
}

@PublishedApi
internal inline fun <reified T> typeName(): String = T::class.qualifiedName ?: "T"