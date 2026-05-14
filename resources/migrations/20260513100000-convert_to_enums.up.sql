CREATE TYPE player_position AS ENUM ('Goalkeeper', 'Defender', 'Midfielder', 'Striker');
--;;
CREATE TYPE member_type AS ENUM ('mensalista', 'mensalista_temporario', 'diarista', 'convidado');
--;;
CREATE TYPE attendance_status AS ENUM ('confirmed', 'declined', 'pending', 'waitlist');
--;;
CREATE TYPE pelada_status AS ENUM ('attendance', 'open', 'running', 'closed');
--;;
CREATE TYPE match_status AS ENUM ('scheduled', 'finished');
--;;
CREATE TYPE timer_status AS ENUM ('stopped', 'running', 'paused');
--;;
CREATE TYPE invitation_status AS ENUM ('pending', 'accepted', 'rejected');
--;;
CREATE TYPE transaction_type AS ENUM ('income', 'expense');
--;;
CREATE TYPE transaction_status AS ENUM ('paid', 'reversed');
--;;
CREATE TYPE match_event_type AS ENUM ('assist', 'goal', 'own_goal');
--;;

-- Update Users table
ALTER TABLE "Users" ALTER COLUMN position TYPE player_position USING position::player_position;
--;;

-- Update OrganizationPlayers table
ALTER TABLE "OrganizationPlayers" ALTER COLUMN member_type DROP DEFAULT;
--;;
ALTER TABLE "OrganizationPlayers" ALTER COLUMN member_type TYPE member_type USING member_type::member_type;
--;;
ALTER TABLE "OrganizationPlayers" ALTER COLUMN member_type SET DEFAULT 'diarista'::member_type;
--;;
ALTER TABLE "OrganizationPlayers" ADD COLUMN position player_position;
--;;
UPDATE "OrganizationPlayers" op
SET position = p.value::player_position
FROM "Positions" p
WHERE op.position_id = p.id;
--;;
ALTER TABLE "OrganizationPlayers" DROP COLUMN position_id;
--;;

-- Update Attendance table
ALTER TABLE "Attendance" ALTER COLUMN status TYPE attendance_status USING status::attendance_status;
--;;

-- Update Peladas table
ALTER TABLE "Peladas" ALTER COLUMN status DROP DEFAULT;
--;;
ALTER TABLE "Peladas" ALTER COLUMN status TYPE pelada_status USING status::pelada_status;
--;;
ALTER TABLE "Peladas" ALTER COLUMN status SET DEFAULT 'open'::pelada_status;
--;;
ALTER TABLE "Peladas" ALTER COLUMN timer_status DROP DEFAULT;
--;;
ALTER TABLE "Peladas" ALTER COLUMN timer_status TYPE timer_status USING timer_status::timer_status;
--;;
ALTER TABLE "Peladas" ALTER COLUMN timer_status SET DEFAULT 'stopped'::timer_status;
--;;

-- Update Matches table
ALTER TABLE "Matches" ALTER COLUMN status DROP DEFAULT;
--;;
ALTER TABLE "Matches" ALTER COLUMN status TYPE match_status USING status::match_status;
--;;
ALTER TABLE "Matches" ALTER COLUMN status SET DEFAULT 'scheduled'::match_status;
--;;
ALTER TABLE "Matches" ALTER COLUMN timer_status DROP DEFAULT;
--;;
ALTER TABLE "Matches" ALTER COLUMN timer_status TYPE timer_status USING timer_status::timer_status;
--;;
ALTER TABLE "Matches" ALTER COLUMN timer_status SET DEFAULT 'stopped'::timer_status;
--;;

-- Update OrganizationInvitations table
ALTER TABLE "OrganizationInvitations" ALTER COLUMN status DROP DEFAULT;
--;;
ALTER TABLE "OrganizationInvitations" ALTER COLUMN status TYPE invitation_status USING status::invitation_status;
--;;
ALTER TABLE "OrganizationInvitations" ALTER COLUMN status SET DEFAULT 'pending'::invitation_status;
--;;

-- Update Transactions table
ALTER TABLE "Transactions" ALTER COLUMN type TYPE transaction_type USING type::transaction_type;
--;;
ALTER TABLE "Transactions" ALTER COLUMN status DROP DEFAULT;
--;;
ALTER TABLE "Transactions" ALTER COLUMN status TYPE transaction_status USING status::transaction_status;
--;;
ALTER TABLE "Transactions" ALTER COLUMN status SET DEFAULT 'paid'::transaction_status;
--;;

-- Update MatchEvents table
ALTER TABLE "MatchEvents" ALTER COLUMN event_type TYPE match_event_type USING event_type::match_event_type;
--;;

-- Cleanup
DROP TABLE "Positions";
