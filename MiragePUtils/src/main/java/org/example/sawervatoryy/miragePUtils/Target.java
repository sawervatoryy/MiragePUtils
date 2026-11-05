package org.example.sawervatoryy.miragePUtils;

import java.util.UUID;

/** Игрок, к которому применяется наказание (онлайн или оффлайн). */
public record Target(UUID uuid, String name) {
}
