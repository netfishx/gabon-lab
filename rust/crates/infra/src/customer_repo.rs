use sqlx::PgPool;

use gabon_shared::error::AppError;
use gabon_shared::traits::CustomerRow;

pub struct PgAuthRepo<'a> {
    pub pool: &'a PgPool,
}

impl gabon_shared::traits::AuthRepo for PgAuthRepo<'_> {
    async fn find_by_username(&self, username: &str) -> Result<Option<CustomerRow>, AppError> {
        let row = sqlx::query_as::<_, PgCustomer>(
            "SELECT id, username, password_hash, name, phone, email, avatar_url, signature, is_vip, diamond_balance FROM customers WHERE LOWER(username) = LOWER($1) AND deleted_at IS NULL",
        )
        .bind(username)
        .fetch_optional(self.pool)
        .await?;

        Ok(row.map(Into::into))
    }

    async fn find_by_id(&self, id: i64) -> Result<Option<CustomerRow>, AppError> {
        let row = sqlx::query_as::<_, PgCustomer>(
            "SELECT id, username, password_hash, name, phone, email, avatar_url, signature, is_vip, diamond_balance FROM customers WHERE id = $1 AND deleted_at IS NULL",
        )
        .bind(id)
        .fetch_optional(self.pool)
        .await?;

        Ok(row.map(Into::into))
    }

    async fn create(&self, username: &str, password_hash: &str) -> Result<CustomerRow, AppError> {
        let row = sqlx::query_as::<_, PgCustomer>(
            r#"INSERT INTO customers (username, password_hash, name)
               VALUES ($1, $2, $1)
               RETURNING id, username, password_hash, name, phone, email, avatar_url, signature, is_vip, diamond_balance"#,
        )
        .bind(username)
        .bind(password_hash)
        .fetch_one(self.pool)
        .await?;

        Ok(row.into())
    }

    async fn update_last_login(&self, id: i64) -> Result<(), AppError> {
        sqlx::query("UPDATE customers SET last_login_at = NOW(), updated_at = NOW() WHERE id = $1")
            .bind(id)
            .execute(self.pool)
            .await?;
        Ok(())
    }

    async fn change_password(&self, id: i64, new_password_hash: &str) -> Result<(), AppError> {
        sqlx::query("UPDATE customers SET password_hash = $1, updated_at = NOW() WHERE id = $2")
            .bind(new_password_hash)
            .bind(id)
            .execute(self.pool)
            .await?;
        Ok(())
    }

    async fn update_profile(
        &self,
        id: i64,
        name: Option<&str>,
        phone: Option<&str>,
        email: Option<&str>,
        signature: Option<&str>,
    ) -> Result<CustomerRow, AppError> {
        let row = sqlx::query_as::<_, PgCustomer>(
            r#"UPDATE customers
               SET name = COALESCE($1, name),
                   phone = COALESCE($2, phone),
                   email = COALESCE($3, email),
                   signature = COALESCE($4, signature),
                   updated_at = NOW()
               WHERE id = $5 AND deleted_at IS NULL
               RETURNING id, username, password_hash, name, phone, email, avatar_url, signature, is_vip, diamond_balance"#,
        )
        .bind(name)
        .bind(phone)
        .bind(email)
        .bind(signature)
        .bind(id)
        .fetch_one(self.pool)
        .await?;

        Ok(row.into())
    }
}

/// Internal PG row — maps sqlx columns, converts to shared CustomerRow.
#[derive(sqlx::FromRow)]
struct PgCustomer {
    id: i64,
    username: String,
    password_hash: String,
    name: Option<String>,
    phone: Option<String>,
    email: Option<String>,
    avatar_url: Option<String>,
    signature: Option<String>,
    is_vip: bool,
    diamond_balance: i64,
}

impl From<PgCustomer> for CustomerRow {
    fn from(r: PgCustomer) -> Self {
        Self {
            id: r.id,
            username: r.username,
            password_hash: r.password_hash,
            name: r.name,
            phone: r.phone,
            email: r.email,
            avatar_url: r.avatar_url,
            signature: r.signature,
            is_vip: r.is_vip,
            diamond_balance: r.diamond_balance,
        }
    }
}
