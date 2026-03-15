-- name: CreatePlayRecord :exec
INSERT INTO video_play_records (video_id, customer_id, play_type, ip_address)
VALUES (@video_id, @customer_id, @play_type, @ip_address);
