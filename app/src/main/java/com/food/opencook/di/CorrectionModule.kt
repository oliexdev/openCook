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

package com.food.opencook.di

import com.food.opencook.repository.SuggestionRepository
import com.food.opencook.util.ImportCorrector
import com.food.opencook.util.IngredientCorrection
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CorrectionModule {

    /**
     * Import-time corrector over the curated bundled vocabulary. Applies only the
     * auto-correct (high-confidence) result; everything else passes through unchanged so
     * the review screen can still suggest the borderline cases.
     */
    @Provides
    @Singleton
    fun provideImportCorrector(suggestions: SuggestionRepository): ImportCorrector {
        val corrector = IngredientCorrection.corrector(suggestions.curatedTerms())
        return ImportCorrector { name ->
            val r = corrector.correct(name)
            if (r.autoCorrected) r.name else name
        }
    }
}
