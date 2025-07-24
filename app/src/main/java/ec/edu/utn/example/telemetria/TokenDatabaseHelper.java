package ec.edu.utn.example.telemetria;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class TokenDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "TokenDatabaseHelper";
    private static final String DATABASE_NAME = "security.db";
    private static final int DATABASE_VERSION = 1;

    // Definición de la tabla y columnas
    public static final String TABLE_VALID_TOKENS = "valid_tokens";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TOKEN = "token";
    public static final String COLUMN_USERNAME = "username";
    public static final String COLUMN_CREATED_AT = "created_at";

    private static final String TABLE_CREATE =
            "CREATE TABLE " + TABLE_VALID_TOKENS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_TOKEN + " TEXT NOT NULL UNIQUE, " +
                    COLUMN_USERNAME + " TEXT NOT NULL, " +
                    COLUMN_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ");";

    public TokenDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_CREATE);
        Log.i(TAG, "Tabla de tokens creada.");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_VALID_TOKENS);
        onCreate(db);
    }

    /**
     * Guarda un nuevo token en la base de datos.
     */
    public void addToken(String token, String username) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TOKEN, token);
        values.put(COLUMN_USERNAME, username);
        db.insert(TABLE_VALID_TOKENS, null, values);
        db.close();
    }

    /**
     * Comprueba si un token existe en la base de datos.
     * @return true si el token es válido y existe, false en caso contrario.
     */
    public boolean isTokenInDb(String token) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT " + COLUMN_TOKEN + " FROM " + TABLE_VALID_TOKENS + " WHERE " + COLUMN_TOKEN + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{token});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        db.close();
        return exists;
    }
}