package model

// Video status lifecycle.
const (
	VideoStatusFailed        int16 = 0
	VideoStatusPendingEncode int16 = 1
	VideoStatusEncoding      int16 = 2
	VideoStatusPendingReview int16 = 3
	VideoStatusApproved      int16 = 4
	VideoStatusRejected      int16 = 5
)

// Play record type.
const (
	PlayTypeClick     int16 = 1
	PlayTypeValidPlay int16 = 2
)

// Admin account status.
const (
	AdminStatusDisabled int16 = 0
	AdminStatusActive   int16 = 1
)

// Admin role.
const (
	AdminRoleSuperadmin int16 = 1
	AdminRoleAdmin      int16 = 2
)

// Task type (period granularity).
const (
	TaskTypeDaily   int16 = 1
	TaskTypeWeekly  int16 = 2
	TaskTypeMonthly int16 = 3
)

// Task progress status.
const (
	TaskStatusInProgress int16 = 1
	TaskStatusCompleted  int16 = 2
	TaskStatusClaimed    int16 = 3
	TaskStatusExpired    int16 = 4
)
