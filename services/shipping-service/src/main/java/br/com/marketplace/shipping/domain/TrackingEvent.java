package br.com.marketplace.shipping.domain;

import java.time.Instant;

/** Uma linha da linha do tempo do envio: onde estava, quando, e se o evento foi aceito. */
public record TrackingEvent(String status, String location, String description, Instant occurredAt, boolean accepted) {}
