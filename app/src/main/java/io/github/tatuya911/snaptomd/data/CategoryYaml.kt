package io.github.tatuya911.snaptomd.data

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

/**
 * 競合するカテゴリ（同名）に対する処理アクション
 */
enum class ConflictAction {
    /** 既存カテゴリをインポート内容で上書き */
    OVERWRITE,
    /** インポート内容を破棄し、既存カテゴリを維持 */
    SKIP
}

/**
 * カテゴリ設定の YAML エクスポート / インポートおよびマージユーティリティ
 */
object CategoryYaml {

    /**
     * カテゴリ一覧を YAML 形式の文字列にエクスポートする。
     * 複数行のテンプレートは Markdown として人間が読み書きしやすいよう '|'（ブロックスカラー）形式で出力する。
     */
    fun export(categories: List<Category>): String {
        val sb = StringBuilder()
        sb.appendLine("version: 1")
        sb.appendLine("categories:")
        for (category in categories) {
            sb.appendLine("  - name: ${formatYamlSingleLine(category.name)}")
            if (category.hint.isNotBlank()) {
                if (category.hint.contains("\n")) {
                    sb.appendLine("    hint: |")
                    category.hint.lines().forEach { line ->
                        if (line.isEmpty()) sb.appendLine() else sb.appendLine("      $line")
                    }
                } else {
                    sb.appendLine("    hint: ${formatYamlSingleLine(category.hint)}")
                }
            }
            val template = category.template
            if (template.contains("\n")) {
                sb.appendLine("    template: |")
                template.lines().forEach { line ->
                    if (line.isEmpty()) sb.appendLine() else sb.appendLine("      $line")
                }
            } else {
                sb.appendLine("    template: ${formatYamlSingleLine(template)}")
            }
        }
        return sb.toString()
    }

    /**
     * YAML 文字列からカテゴリ一覧をインポートする。
     * パースに失敗した場合は [Result.failure]（日本語メッセージ付き例外）を返す。
     */
    fun importYaml(
        yamlText: String,
        defaultTemplate: String = ApiKeyManager.DEFAULT_TEMPLATE
    ): Result<List<Category>> {
        if (yamlText.isBlank()) {
            return Result.failure(IllegalArgumentException("ファイルが空です。"))
        }

        val rootObject: Any? = try {
            val settings = LoadSettings.builder().build()
            val load = Load(settings)
            load.loadFromString(yamlText)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("YAMLの構文が正しくありません: ${e.localizedMessage}", e))
        }

        val rootMap = rootObject as? Map<*, *>
            ?: return Result.failure(IllegalArgumentException("YAMLのルートはオブジェクト形式である必要があります。"))

        val categoriesList = rootMap["categories"] as? List<*>
            ?: return Result.failure(IllegalArgumentException("YAML内に 'categories' リストが見つかりません。"))

        val result = mutableListOf<Category>()
        categoriesList.forEachIndexed { index, item ->
            val itemMap = item as? Map<*, *>
                ?: return Result.failure(IllegalArgumentException("${index + 1}番目のカテゴリの形式が不正です（オブジェクトではありません）。"))

            val rawName = itemMap["name"]?.toString()?.trim()
            if (rawName.isNullOrEmpty()) {
                return Result.failure(IllegalArgumentException("${index + 1}番目のカテゴリに 'name' が指定されていないか、空です。"))
            }

            val rawHint = itemMap["hint"]?.toString() ?: ""
            val rawTemplate = itemMap["template"]?.toString()?.ifBlank { defaultTemplate } ?: defaultTemplate

            result.add(Category(name = rawName, template = rawTemplate, hint = rawHint))
        }

        if (result.isEmpty()) {
            return Result.failure(IllegalArgumentException("インポート対象のカテゴリが1件もありません。"))
        }

        return Result.success(result)
    }

    /**
     * 現在のカテゴリ一覧 [current] と、インポート対象のカテゴリ一覧 [imported] を比較し、
     * 同名で競合しているカテゴリのリスト（imported 側の定義）を返す。
     */
    fun findConflicts(current: List<Category>, imported: List<Category>): List<Category> {
        val currentNames = current.map { it.name }.toSet()
        val seenImported = mutableSetOf<String>()
        val conflicts = mutableListOf<Category>()
        for (item in imported) {
            if (item.name in currentNames && seenImported.add(item.name)) {
                conflicts.add(item)
            }
        }
        return conflicts
    }

    /**
     * [current] と [imported] を指定された競合解決ポリシー [resolutions]（カテゴリ名 -> OVERWRITE or SKIP）に基づいてマージする。
     * - [current] に存在しないカテゴリは新規追加（「その他」の手前に挿入）
     * - 競合カテゴリで [ConflictAction.OVERWRITE] が指定されたものは、imported の内容で上書き（並び順は current を維持）
     * - 競合カテゴリで [ConflictAction.SKIP]（または未指定）のものは、current の内容を維持
     * - 最後に [Category.normalize] を通して重複除去および「その他」の末尾配置を保証する
     */
    fun merge(
        current: List<Category>,
        imported: List<Category>,
        resolutions: Map<String, ConflictAction>,
        defaultTemplate: String = ApiKeyManager.DEFAULT_TEMPLATE
    ): List<Category> {
        // 重複除去した imported のマップ（先勝ち）
        val importedMap = mutableMapOf<String, Category>()
        for (item in imported) {
            if (!importedMap.containsKey(item.name)) {
                importedMap[item.name] = item
            }
        }

        // 1. current のカテゴリを更新または維持
        val mergedList = current.map { cur ->
            val importedCat = importedMap[cur.name]
            if (importedCat != null && resolutions[cur.name] == ConflictAction.OVERWRITE) {
                // 上書き
                importedCat
            } else {
                // 維持
                cur
            }
        }.toMutableList()

        // 2. imported のうち current に存在しなかった新規カテゴリを抽出
        val currentNames = current.map { it.name }.toSet()
        val newCategories = imported.filter { it.name !in currentNames && it.name != Category.OTHER_NAME }

        // 重複除去した新規カテゴリ
        val uniqueNew = mutableListOf<Category>()
        val seenNew = mutableSetOf<String>()
        for (item in newCategories) {
            if (seenNew.add(item.name)) {
                uniqueNew.add(item)
            }
        }

        // 「その他」の位置を探し、その手前に新規カテゴリを挿入する（なければ末尾に追加）
        val otherIndex = mergedList.indexOfFirst { it.isOther }
        if (otherIndex >= 0) {
            mergedList.addAll(otherIndex, uniqueNew)
        } else {
            mergedList.addAll(uniqueNew)
        }

        return Category.normalize(mergedList, defaultTemplate)
    }

    private fun formatYamlSingleLine(str: String): String {
        // コロンや記号を含む文字列は安全のためにダブルクォートでエスケープ
        val escaped = str.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }
}
