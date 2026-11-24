package com.example.engine

class LedgerLabException(
    val httpStatus: Int,
    val errorCode: String,
    override val message: String,
    val traceId: String = CryptoUtils.generatePrefixedId("trc")
) : RuntimeException(message) {
    companion object {
        fun badRequest(msg: String) = LedgerLabException(400, "INVALID_REQUEST", msg)
        fun unauthorized(msg: String) = LedgerLabException(401, "UNAUTHORIZED", msg)
        fun forbidden(msg: String) = LedgerLabException(403, "FORBIDDEN", msg)
        fun notFound(msg: String) = LedgerLabException(404, "RESOURCE_NOT_FOUND", msg)
        fun conflict(msg: String) = LedgerLabException(409, "CONFLICT", msg)
        fun financialValidation(msg: String) = LedgerLabException(422, "FINANCIAL_VALIDATION_ERROR", msg)
        fun rateLimited(msg: String) = LedgerLabException(429, "RATE_LIMITED", msg)
        fun internal(msg: String) = LedgerLabException(500, "INTERNAL_SERVER_ERROR", msg)
    }
}
