package br.com.marketplace.ingestion.domain;

/** O lote inteiro e invalido (cabecalho quebrado): nada e aproveitado, vai para a DLQ. */
public class InvalidBatchException extends RuntimeException {

    public InvalidBatchException(String message) {
        super(message);
    }
}
