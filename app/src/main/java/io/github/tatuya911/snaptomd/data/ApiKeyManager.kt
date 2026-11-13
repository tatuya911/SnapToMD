package io.github.tatuya911.snaptomd.data

import android.content.Context
import androidx.core.content.edit

/**
 * APIキー、モデル、Markdownテンプレート設定を端末内に保存・管理するクラス
 */
class ApiKeyManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences(
        "app_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_MODEL_NAME = "gemini_model_name"
        private const val KEY_CATEGORIES = "categories_json"
        private const val KEY_MARKDOWN_DIR_URI = "markdown_dir_uri"
        private const val KEY_IMAGE_DIR_URI = "image_dir_uri"

        /** テンプレート内で画像ファイル名に置換されるプレースホルダ */
        const val IMAGE_PLACEHOLDER = "{{image}}"

        const val MOCK_KEY = "MOCK_KEY"
        const val DEFAULT_MODEL = "gemini-3.8-flash"

        /** Markdown出力フォーマットの既定テンプレート */
        const val DEFAULT_TEMPLATE = """# [商品名 / ブランド名]

## 概要
- **価格 (実売/定価):** 
- **メーカー/ブランド:** 
- **分類/カテゴリ:** 

## 特徴・主なスペック
- [特徴1]
- [特徴2]
- [特徴3]

## リンク・参考情報
- [公式/販売ページへのWeb検索参照リンク]
"""
    }

    fun saveApiKey(key: String) {
        sharedPreferences.edit { putString(KEY_GEMINI_API_KEY, key.trim()) }
    }

    fun getApiKey(): String {
        return sharedPreferences.getString(KEY_GEMINI_API_KEY, "") ?: ""
    }

    fun saveModelName(modelName: String) {
        sharedPreferences.edit { putString(KEY_MODEL_NAME, modelName.trim()) }
    }

    fun getModelName(): String {
        val saved = sharedPreferences.getString(KEY_MODEL_NAME, null)
        return if (!saved.isNullOrBlank()) saved else DEFAULT_MODEL
    }

    /** カテゴリ一覧を保存する（「その他」の補完・重複除外を行う） */
    fun saveCategories(categories: List<Category>) {
        val normalized = Category.normalize(categories, DEFAULT_TEMPLATE)
        sharedPreferences.edit { putString(KEY_CATEGORIES, Category.toJson(normalized)) }
    }

    /** カテゴリ一覧。「その他」は必ず末尾に含まれる。未保存時は「その他」のみ */
    fun getCategories(): List<Category> {
        val json = sharedPreferences.getString(KEY_CATEGORIES, null)
        val saved = if (json.isNullOrBlank()) emptyList() else {
            try { Category.fromJson(json) } catch (e: Exception) { emptyList() }
        }
        return Category.normalize(saved, DEFAULT_TEMPLATE)
    }

    fun saveMarkdownDirUri(uri: String) {
        sharedPreferences.edit { putString(KEY_MARKDOWN_DIR_URI, uri) }
    }

    fun getMarkdownDirUri(): String? {
        return sharedPreferences.getString(KEY_MARKDOWN_DIR_URI, null)?.takeIf { it.isNotBlank() }
    }

    fun saveImageDirUri(uri: String) {
        sharedPreferences.edit { putString(KEY_IMAGE_DIR_URI, uri) }
    }

    fun getImageDirUri(): String? {
        return sharedPreferences.getString(KEY_IMAGE_DIR_URI, null)?.takeIf { it.isNotBlank() }
    }

    /** Markdown・画像の両保存先が設定済みか */
    fun isStorageConfigured(): Boolean {
        return getMarkdownDirUri() != null && getImageDirUri() != null
    }

    fun isConfigured(): Boolean {
        return getApiKey().isNotBlank()
    }

    fun isMockMode(): Boolean {
        val key = getApiKey()
        return key.equals(MOCK_KEY, ignoreCase = true) || key.equals("TEST", ignoreCase = true)
    }
}
