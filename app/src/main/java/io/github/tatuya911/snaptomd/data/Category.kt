package io.github.tatuya911.snaptomd.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * ノートのカテゴリ。カテゴリごとにMarkdownテンプレートと判定のヒントを持つ
 *
 * @param name カテゴリ名
 * @param template このカテゴリで使うMarkdownテンプレート
 * @param hint AIがカテゴリを判定する際の補足説明（任意）
 */
data class Category(
    val name: String,
    val template: String,
    val hint: String = ""
) {
    val isOther: Boolean get() = name == OTHER_NAME

    companion object {
        /** 必須の「その他」カテゴリ名（削除・改名不可） */
        const val OTHER_NAME = "その他"

        /** 名前の重複・空を除外し、「その他」が必ず末尾に1つだけ存在する形に正規化する */
        fun normalize(list: List<Category>, defaultTemplate: String): List<Category> {
            val seen = mutableSetOf<String>()
            val others = mutableListOf<Category>()
            val normal = list.mapNotNull {
                val name = it.name.trim()
                when {
                    name.isEmpty() -> null
                    name == OTHER_NAME -> { if (others.isEmpty()) others.add(it.copy(name = name)); null }
                    !seen.add(name) -> null
                    else -> it.copy(name = name)
                }
            }
            val other = others.firstOrNull() ?: Category(OTHER_NAME, defaultTemplate)
            return normal + other
        }

        fun toJson(list: List<Category>): String {
            val array = JSONArray()
            list.forEach {
                array.put(
                    JSONObject()
                        .put("name", it.name)
                        .put("template", it.template)
                        .put("hint", it.hint)
                )
            }
            return array.toString()
        }

        fun fromJson(json: String): List<Category> {
            val array = JSONArray(json)
            return (0 until array.length()).map {
                val obj = array.getJSONObject(it)
                Category(
                    name = obj.optString("name"),
                    template = obj.optString("template"),
                    hint = obj.optString("hint")
                )
            }
        }
    }
}
