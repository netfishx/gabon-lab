package observability

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestSetupOTel_EmptyEndpoint(t *testing.T) {
	lp, shutdown, err := SetupOTel(context.Background(), "", "test-svc", "test")
	require.NoError(t, err)
	assert.Nil(t, lp)
	assert.NotNil(t, shutdown)
	assert.NoError(t, shutdown(context.Background()))
}
