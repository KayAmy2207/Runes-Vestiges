package fr.runevestiges.economy;

import java.util.UUID;

public record Wallet(UUID playerId, long balance) {
	public Wallet {
		if (balance < 0L) {
			throw new IllegalArgumentException("A wallet balance cannot be negative.");
		}
	}
}
