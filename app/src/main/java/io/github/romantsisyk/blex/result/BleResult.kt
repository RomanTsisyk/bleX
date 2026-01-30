package io.github.romantsisyk.blex.result

import io.github.romantsisyk.blex.exception.BleException

/**
 * A discriminated union that encapsulates the result of a BLE operation.
 *
 * This sealed class provides a type-safe way to handle both successful results
 * and failures from BLE operations without using nullable types or exceptions
 * for control flow.
 *
 * ## Design Philosophy
 *
 * [BleResult] is inspired by Kotlin's [Result] class but is specifically designed
 * for BLE operations with [BleException] as the failure type. This provides:
 *
 * - **Type safety**: The compiler ensures all failure cases are handled
 * - **Explicit error handling**: Errors are part of the return type, not hidden
 * - **Functional composition**: Chain operations with [map], [flatMap], etc.
 * - **No exceptions for control flow**: Cleaner code without try-catch blocks
 *
 * ## Usage Examples
 *
 * ### Basic Usage
 *
 * ```kotlin
 * val result = connection.readCharacteristicResult(serviceUuid, charUuid)
 *
 * when (result) {
 *     is BleResult.Success -> {
 *         val data = result.data
 *         processData(data)
 *     }
 *     is BleResult.Failure -> {
 *         val error = result.error
 *         handleError(error)
 *     }
 * }
 * ```
 *
 * ### Functional Style
 *
 * ```kotlin
 * connection.readCharacteristicResult(serviceUuid, charUuid)
 *     .map { bytes -> String(bytes, Charsets.UTF_8) }
 *     .onSuccess { text -> displayText(text) }
 *     .onFailure { error -> showError(error.message) }
 * ```
 *
 * ### Chaining Operations
 *
 * ```kotlin
 * connection.readCharacteristicResult(serviceUuid, charUuid)
 *     .flatMap { data ->
 *         parseResponse(data)?.let { bleSuccess(it) }
 *             ?: bleFailure(BleCharacteristicException("Invalid response format"))
 *     }
 *     .onSuccess { parsedData -> process(parsedData) }
 * ```
 *
 * ### Extracting Values
 *
 * ```kotlin
 * // Safe extraction (returns null on failure)
 * val data: ByteArray? = result.getOrNull()
 *
 * // With default value
 * val data: ByteArray = result.getOrDefault(byteArrayOf())
 *
 * // Throws on failure
 * val data: ByteArray = result.getOrThrow()
 * ```
 *
 * @param T The type of the success value.
 *
 * @see BleException
 * @see bleSuccess
 * @see bleFailure
 */
sealed class BleResult<out T> {

    /**
     * Represents a successful BLE operation result.
     *
     * @property data The successful result data.
     * @param T The type of the success value.
     */
    data class Success<T>(val data: T) : BleResult<T>()

    /**
     * Represents a failed BLE operation result.
     *
     * @property error The [BleException] describing the failure.
     */
    data class Failure(val error: BleException) : BleResult<Nothing>()

    /**
     * Returns `true` if this result represents a successful operation.
     *
     * ```kotlin
     * if (result.isSuccess) {
     *     val data = result.getOrNull()!!
     *     // Process data...
     * }
     * ```
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * Returns `true` if this result represents a failed operation.
     *
     * ```kotlin
     * if (result.isFailure) {
     *     val error = result.exceptionOrNull()!!
     *     Log.e("BLE", "Operation failed: ${error.message}")
     * }
     * ```
     */
    val isFailure: Boolean get() = this is Failure

    /**
     * Returns the success value if this is a [Success], or `null` if this is a [Failure].
     *
     * This method provides safe access to the result data without throwing exceptions.
     *
     * ```kotlin
     * val data: ByteArray? = result.getOrNull()
     * data?.let { processData(it) }
     * ```
     *
     * @return The success value, or `null` if this is a failure.
     */
    fun getOrNull(): T? = (this as? Success)?.data

    /**
     * Returns the exception if this is a [Failure], or `null` if this is a [Success].
     *
     * Use this method to safely access the error without pattern matching.
     *
     * ```kotlin
     * result.exceptionOrNull()?.let { error ->
     *     Log.e("BLE", "Error: ${error.message}, GATT: ${error.gattStatus}")
     * }
     * ```
     *
     * @return The [BleException] if this is a failure, or `null` if this is a success.
     */
    fun exceptionOrNull(): BleException? = (this as? Failure)?.error

    /**
     * Returns the success value if this is a [Success], or throws the exception if this is a [Failure].
     *
     * Use this method when you want to propagate failures as exceptions.
     *
     * ```kotlin
     * try {
     *     val data = result.getOrThrow()
     *     processData(data)
     * } catch (e: BleException) {
     *     handleError(e)
     * }
     * ```
     *
     * @return The success value.
     * @throws BleException If this is a [Failure].
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw error
    }

    /**
     * Returns the success value if this is a [Success], or the [defaultValue] if this is a [Failure].
     *
     * ```kotlin
     * val data: ByteArray = result.getOrDefault(byteArrayOf())
     * ```
     *
     * @param defaultValue The value to return if this is a failure.
     * @return The success value or the default value.
     */
    fun getOrDefault(defaultValue: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Failure -> defaultValue
    }

    /**
     * Returns the success value if this is a [Success], or the result of [defaultValue] if this is a [Failure].
     *
     * The [defaultValue] function is only called if this is a failure, making it suitable
     * for expensive computations.
     *
     * ```kotlin
     * val data: ByteArray = result.getOrElse { error ->
     *     Log.w("BLE", "Using fallback due to: ${error.message}")
     *     loadFromCache()
     * }
     * ```
     *
     * @param defaultValue A function that provides the default value, receiving the error as a parameter.
     * @return The success value or the computed default value.
     */
    inline fun getOrElse(defaultValue: (BleException) -> @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Failure -> defaultValue(error)
    }

    /**
     * Transforms the success value using the given [transform] function.
     *
     * If this is a [Success], returns a new [Success] with the transformed value.
     * If this is a [Failure], returns the same [Failure] unchanged.
     *
     * ```kotlin
     * val stringResult: BleResult<String> = byteArrayResult
     *     .map { bytes -> String(bytes, Charsets.UTF_8) }
     * ```
     *
     * @param transform The function to apply to the success value.
     * @return A new [BleResult] with the transformed value, or the original failure.
     */
    inline fun <R> map(transform: (T) -> R): BleResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    /**
     * Transforms the success value using a function that returns a [BleResult].
     *
     * This is useful for chaining operations that can also fail.
     *
     * ```kotlin
     * val result: BleResult<ParsedData> = connection
     *     .readCharacteristicResult(serviceUuid, charUuid)
     *     .flatMap { bytes -> parseData(bytes) } // parseData returns BleResult<ParsedData>
     * ```
     *
     * @param transform The function to apply to the success value, returning a new [BleResult].
     * @return The result of [transform] if this is a success, or the original failure.
     */
    inline fun <R> flatMap(transform: (T) -> BleResult<R>): BleResult<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
    }

    /**
     * Transforms the error using the given [transform] function.
     *
     * If this is a [Failure], returns a new [Failure] with the transformed error.
     * If this is a [Success], returns the same [Success] unchanged.
     *
     * ```kotlin
     * val result = operation()
     *     .mapError { error ->
     *         BleConnectionException("Wrapped: ${error.message}", error.gattStatus)
     *     }
     * ```
     *
     * @param transform The function to apply to the error.
     * @return A new [BleResult] with the transformed error, or the original success.
     */
    inline fun mapError(transform: (BleException) -> BleException): BleResult<T> = when (this) {
        is Success -> this
        is Failure -> Failure(transform(error))
    }

    /**
     * Recovers from a failure by providing an alternative success value.
     *
     * If this is a [Failure], returns a [Success] with the recovered value.
     * If this is a [Success], returns the same [Success] unchanged.
     *
     * ```kotlin
     * val result: BleResult<ByteArray> = connection
     *     .readCharacteristicResult(serviceUuid, charUuid)
     *     .recover { error ->
     *         Log.w("BLE", "Using cached value due to: ${error.message}")
     *         cachedValue
     *     }
     * ```
     *
     * @param recovery The function that provides the recovery value.
     * @return A [Success] with either the original or recovered value.
     */
    inline fun recover(recovery: (BleException) -> @UnsafeVariance T): BleResult<T> = when (this) {
        is Success -> this
        is Failure -> Success(recovery(error))
    }

    /**
     * Attempts to recover from a failure using another [BleResult].
     *
     * If this is a [Failure], returns the result of the [recovery] function.
     * If this is a [Success], returns the same [Success] unchanged.
     *
     * ```kotlin
     * val result = primaryConnection
     *     .readCharacteristicResult(serviceUuid, charUuid)
     *     .recoverWith { error ->
     *         Log.w("BLE", "Primary failed, trying backup: ${error.message}")
     *         backupConnection.readCharacteristicResult(serviceUuid, charUuid)
     *     }
     * ```
     *
     * @param recovery The function that provides the recovery [BleResult].
     * @return The original success or the recovery result.
     */
    inline fun recoverWith(recovery: (BleException) -> BleResult<@UnsafeVariance T>): BleResult<T> = when (this) {
        is Success -> this
        is Failure -> recovery(error)
    }

    /**
     * Performs the given [action] if this is a [Success].
     *
     * Returns `this` for chaining.
     *
     * ```kotlin
     * result
     *     .onSuccess { data -> saveToDatabase(data) }
     *     .onFailure { error -> logError(error) }
     * ```
     *
     * @param action The action to perform with the success value.
     * @return This [BleResult] for chaining.
     */
    inline fun onSuccess(action: (T) -> Unit): BleResult<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * Performs the given [action] if this is a [Failure].
     *
     * Returns `this` for chaining.
     *
     * ```kotlin
     * result
     *     .onSuccess { data -> saveToDatabase(data) }
     *     .onFailure { error -> logError(error) }
     * ```
     *
     * @param action The action to perform with the error.
     * @return This [BleResult] for chaining.
     */
    inline fun onFailure(action: (BleException) -> Unit): BleResult<T> {
        if (this is Failure) action(error)
        return this
    }

    /**
     * Folds the result into a single value by applying the appropriate function.
     *
     * ```kotlin
     * val message: String = result.fold(
     *     onSuccess = { data -> "Received ${data.size} bytes" },
     *     onFailure = { error -> "Error: ${error.message}" }
     * )
     * ```
     *
     * @param onSuccess The function to apply if this is a success.
     * @param onFailure The function to apply if this is a failure.
     * @return The result of applying the appropriate function.
     */
    inline fun <R> fold(
        onSuccess: (T) -> R,
        onFailure: (BleException) -> R
    ): R = when (this) {
        is Success -> onSuccess(data)
        is Failure -> onFailure(error)
    }
}

/**
 * Creates a successful [BleResult] containing the given [data].
 *
 * This is a convenience function for creating [BleResult.Success] instances.
 *
 * ```kotlin
 * fun parseData(bytes: ByteArray): BleResult<ParsedData> {
 *     return try {
 *         val parsed = parser.parse(bytes)
 *         bleSuccess(parsed)
 *     } catch (e: ParseException) {
 *         bleFailure(BleCharacteristicException("Parse failed: ${e.message}"))
 *     }
 * }
 * ```
 *
 * @param data The success value.
 * @return A [BleResult.Success] containing [data].
 *
 * @see BleResult.Success
 * @see bleFailure
 */
fun <T> bleSuccess(data: T): BleResult<T> = BleResult.Success(data)

/**
 * Creates a failed [BleResult] containing the given [error].
 *
 * This is a convenience function for creating [BleResult.Failure] instances.
 *
 * ```kotlin
 * fun parseData(bytes: ByteArray): BleResult<ParsedData> {
 *     if (bytes.isEmpty()) {
 *         return bleFailure(BleCharacteristicException("Empty response"))
 *     }
 *     // ... parsing logic
 * }
 * ```
 *
 * @param error The [BleException] describing the failure.
 * @return A [BleResult.Failure] containing [error].
 *
 * @see BleResult.Failure
 * @see bleSuccess
 */
fun bleFailure(error: BleException): BleResult<Nothing> = BleResult.Failure(error)

/**
 * Wraps the execution of [block] in a [BleResult].
 *
 * If [block] completes successfully, returns [BleResult.Success] with the result.
 * If [block] throws a [BleException], returns [BleResult.Failure] with that exception.
 * Other exceptions are not caught and will propagate.
 *
 * ```kotlin
 * val result: BleResult<ByteArray> = runCatchingBle {
 *     connection.readCharacteristic(serviceUuid, charUuid)
 *         ?: throw BleCharacteristicException("Read returned null")
 * }
 * ```
 *
 * @param block The block of code to execute.
 * @return A [BleResult] containing either the success value or the caught [BleException].
 *
 * @see bleSuccess
 * @see bleFailure
 */
inline fun <T> runCatchingBle(block: () -> T): BleResult<T> {
    return try {
        bleSuccess(block())
    } catch (e: BleException) {
        bleFailure(e)
    }
}

/**
 * Combines two [BleResult] instances into a single result containing a [Pair].
 *
 * If both results are successful, returns a [Success] containing a pair of both values.
 * If either result is a failure, returns the first encountered [Failure].
 *
 * ```kotlin
 * val nameResult = connection.readCharacteristicResult(serviceUuid, nameCharUuid)
 * val versionResult = connection.readCharacteristicResult(serviceUuid, versionCharUuid)
 *
 * val combined: BleResult<Pair<ByteArray, ByteArray>> = nameResult.zip(versionResult)
 * combined.onSuccess { (name, version) ->
 *     Log.i("BLE", "Device: ${String(name)}, Version: ${String(version)}")
 * }
 * ```
 *
 * @param other The other [BleResult] to combine with.
 * @return A [BleResult] containing a [Pair] of both values, or the first failure.
 */
fun <T, R> BleResult<T>.zip(other: BleResult<R>): BleResult<Pair<T, R>> = when (this) {
    is BleResult.Success -> when (other) {
        is BleResult.Success -> bleSuccess(this.data to other.data)
        is BleResult.Failure -> other
    }
    is BleResult.Failure -> this
}
