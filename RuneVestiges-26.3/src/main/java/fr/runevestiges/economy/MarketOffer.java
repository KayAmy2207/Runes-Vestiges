package fr.runevestiges.economy;

import java.util.UUID;

public record MarketOffer(long id, long listingId, UUID buyerId, String buyerName, long amount, long createdAt) {
}
