package com.example.recepiesapp

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Room
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        RecipeIngredientEntity::class,
        TagEntity::class,
        RecipeTagEntity::class,
        CategoryEntity::class,
        IngredientNutritionEntity::class,
    ],
    version = 3,
)
@TypeConverters(Converters::class)
abstract class RecipeDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun tagDao(): TagDao
    abstract fun categoryDao(): CategoryDao
    abstract fun recipeIngredientDao(): RecipeIngredientDao
    abstract fun recipeTagDao(): RecipeTagDao
    abstract fun ingredientNutritionDao(): IngredientNutritionDao

    companion object {
        @Volatile
        private var INSTANCE: RecipeDatabase? = null

        fun getInstance(context: Context): RecipeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RecipeDatabase::class.java,
                    "recipes.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Добавляем поле избранного к существующей таблице рецептов.
                // SQLite хранит BOOLEAN как INTEGER (0/1).
                db.execSQL("ALTER TABLE recipes ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")

                // Нормализованные таблицы (для требований диплома).
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ingredients (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ingredients_name ON ingredients(name)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recipe_ingredients (
                        recipeId INTEGER NOT NULL,
                        ingredientId INTEGER NOT NULL,
                        amount TEXT,
                        PRIMARY KEY(recipeId, ingredientId),
                        FOREIGN KEY(recipeId) REFERENCES recipes(id) ON DELETE CASCADE,
                        FOREIGN KEY(ingredientId) REFERENCES ingredients(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_ingredients_recipeId ON recipe_ingredients(recipeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_ingredients_ingredientId ON recipe_ingredients(ingredientId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_name ON tags(name)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recipe_tags (
                        recipeId INTEGER NOT NULL,
                        tagId INTEGER NOT NULL,
                        PRIMARY KEY(recipeId, tagId),
                        FOREIGN KEY(recipeId) REFERENCES recipes(id) ON DELETE CASCADE,
                        FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_tags_recipeId ON recipe_tags(recipeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_tags_tagId ON recipe_tags(tagId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name ON categories(name)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ingredients: переносим количество/единицу в таблицу ingredients
                db.execSQL("ALTER TABLE ingredients ADD COLUMN quantity REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE ingredients ADD COLUMN unit TEXT NOT NULL DEFAULT ''")
                db.execSQL("DROP INDEX IF EXISTS index_ingredients_name")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ingredients_name_quantity_unit ON ingredients(name, quantity, unit)")

                // recipe_ingredients: убираем amount (оставляем только связь recipeId-ingredientId)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recipe_ingredients_new (
                        recipeId INTEGER NOT NULL,
                        ingredientId INTEGER NOT NULL,
                        PRIMARY KEY(recipeId, ingredientId),
                        FOREIGN KEY(recipeId) REFERENCES recipes(id) ON DELETE CASCADE,
                        FOREIGN KEY(ingredientId) REFERENCES ingredients(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO recipe_ingredients_new(recipeId, ingredientId)
                    SELECT recipeId, ingredientId FROM recipe_ingredients
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE recipe_ingredients")
                db.execSQL("ALTER TABLE recipe_ingredients_new RENAME TO recipe_ingredients")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_ingredients_recipeId ON recipe_ingredients(recipeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_ingredients_ingredientId ON recipe_ingredients(ingredientId)")

                // ingredient_nutrition: справочник КБЖУ на 100г
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ingredient_nutrition (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        caloriesPer100 REAL NOT NULL,
                        proteinPer100 REAL NOT NULL,
                        fatPer100 REAL NOT NULL,
                        carbsPer100 REAL NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ingredient_nutrition_name ON ingredient_nutrition(name)")
            }
        }
    }
}

