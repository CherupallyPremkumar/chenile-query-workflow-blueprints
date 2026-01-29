package org.chenile.query.service.error;

public enum ErrorCodes {
	QUERY_ID_NOT_FOUND(700),
	COUNT_QUERY_DOES_NOT_RETURN_INT(720),
	CANNOT_EXECUTE_QUERY(721),
	CANNOT_EXECUTE_COUNT_QUERY(722);
	private final int subError;
	private ErrorCodes(int subError) {
		this.subError = subError;
	}

	public int getSubError() {
		return this.subError;
	}
}
