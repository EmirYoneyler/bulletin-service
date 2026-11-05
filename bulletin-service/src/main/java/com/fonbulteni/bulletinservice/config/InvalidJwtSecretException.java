package com.fonbulteni.bulletinservice.config;

/** Thrown at startup when {@code JWT_SECRET} is missing or too short. */
public class InvalidJwtSecretException extends IllegalStateException {

    public InvalidJwtSecretException(String message) {
        super(message);
    }
}
