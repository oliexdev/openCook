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

package com.food.opencook.ui.discover

import android.webkit.CookieManager
import android.webkit.WebStorage
import java.net.URI

/**
 * What a site remembered about this phone after a visit in the in-app browser.
 *
 * The browser deliberately keeps cookies — otherwise every visit starts with the consent banner
 * again — so each tile can hand its own site back to zero. Per site rather than in one global
 * switch: the state belongs to that site, and it is where you already are when it annoys you.
 *
 * Honest about its reach: cookies and web storage of the site's **own** origin go away. What an
 * embedded third party (an ad or consent iframe on another domain) stored under its own origin
 * survives, and the engine's HTTP cache cannot be scoped to one host at all — it holds no
 * identity, only copies of pages.
 */
object BrowserData {

    /** Has this site put anything on the phone? Drives whether the tile offers to clear it. */
    fun hasDataFor(url: String): Boolean =
        !CookieManager.getInstance().getCookie(url).isNullOrBlank()

    /**
     * Expire every cookie this site can see and drop its web storage. There is no "delete the
     * cookies of one host" call, so each one is overwritten with an expiry in the past — for the
     * host itself and for its registrable domain, which is where a site usually files them.
     */
    fun clearSite(url: String) {
        val cookies = CookieManager.getInstance()
        val host = runCatching { URI(url).host }.getOrNull().orEmpty()
        cookies.getCookie(url)
            ?.split(';')
            ?.mapNotNull { it.substringBefore('=').trim().takeIf(String::isNotEmpty) }
            ?.forEach { name ->
                cookies.setCookie(url, "$name=; Max-Age=0; Path=/")
                registrableDomain(host)?.let {
                    cookies.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=.$it")
                }
            }
        cookies.flush()

        // Origins are keyed like "https://www.chefkoch.de:443", so match on the host.
        WebStorage.getInstance().getOrigins { origins ->
            origins.keys
                .filterIsInstance<String>()
                .filter { it.contains(host, ignoreCase = true) }
                .forEach { WebStorage.getInstance().deleteOrigin(it) }
        }
    }

    /** "www.chefkoch.de" → "chefkoch.de"; null when the host is not a domain. */
    private fun registrableDomain(host: String): String? {
        val parts = host.split('.').filter { it.isNotEmpty() }
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else null
    }
}
