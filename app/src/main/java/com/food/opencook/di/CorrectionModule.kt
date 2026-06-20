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
