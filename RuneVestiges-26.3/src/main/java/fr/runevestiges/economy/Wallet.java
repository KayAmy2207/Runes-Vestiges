package fr.runevestiges.economy;

import java.util.UUID;

public record Wallet(UUID playerId, long balance) {
   public Wallet(UUID playerId, long balance) {
      if (balance < 0L) {
         throw new IllegalArgumentException("A wallet balance cannot be negative.");
      } else {
         this.playerId = playerId;
         this.balance = balance;
      }
   }
}
