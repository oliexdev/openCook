/*
 *  openCook
 *  Copyright (C) 2026 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.food.opencook.sync

import com.food.opencook.data.remote.dto.MerkleDto

/**
 * Wire conversion between the in-memory [Merkle] trie and its transport DTO.
 * Hashes travel as unsigned 32-bit values (Long) so they compare identically
 * against the Python server's masked ints; locally they stay signed Ints with
 * the same bit pattern.
 */
fun Merkle.toDto(): MerkleDto =
    MerkleDto(
        hash = unsignedHash(),
        children = children.entries.associate { it.key.toString() to it.value.toDto() },
    )

fun MerkleDto.toMerkle(): Merkle {
    val node = Merkle()
    node.hash = hash.toInt()
    children.forEach { (key, child) ->
        key.firstOrNull()?.let { node.children[it] = child.toMerkle() }
    }
    return node
}
