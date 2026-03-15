package config

import (
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestLoad_Defaults(t *testing.T) {
	t.Setenv("DATABASE_URL", "postgres://localhost/test")
	t.Setenv("REDIS_URL", "redis://localhost:6379/0")
	t.Setenv("JWT_CUSTOMER_SECRET", "test-secret-32-chars-minimum!!!!!")
	t.Setenv("JWT_ADMIN_SECRET", "test-admin-secret-32-chars-min!!!!!")
	t.Setenv("S3_ENDPOINT", "http://localhost:3900")
	t.Setenv("S3_ACCESS_KEY", "test-access-key")
	t.Setenv("S3_SECRET_KEY", "test-secret-key")

	cfg, err := Load()
	require.NoError(t, err)
	assert.Equal(t, 8080, cfg.Port)
	assert.Equal(t, "postgres://localhost/test", cfg.DatabaseURL)
}

func TestLoad_MissingRequired(t *testing.T) {
	os.Clearenv()
	_, err := Load()
	assert.Error(t, err)
}
