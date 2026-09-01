package com.coatt.tascade.network;

public final class Events {
  private Events() {}
  // same on backend
  // Client → Server
  public static final String AUTH_VERIFY          = "auth:verify";
  public static final String AUTH_RESTORE         = "auth:restore";
  public static final String JOIN_QUEUE           = "queue:join";
  public static final String LEAVE_QUEUE          = "queue:leave";
  public static final String SOLO_START           = "solo:start";
  public static final String SOLO_FINISH          = "solo:finish";
  public static final String SOLO_ABANDON         = "solo:abandon";
  public static final String SOLO_SEED_CHANGE     = "solo:seed_change";
  public static final String MATCH_FORFEIT        = "match:forfeit";
  public static final String MATCH_SEED_VOTE      = "match:seed_vote";
  public static final String MATCH_DRAW_VOTE      = "match:draw_vote";
  public static final String CREATE_ROOM          = "room:create";
  public static final String JOIN_ROOM            = "room:join";
  public static final String LEAVE_ROOM           = "room:leave";
  public static final String ROOM_SETTINGS_UPDATE = "room:settings_update";
  public static final String ROOM_START           = "room:start";
  public static final String ROOM_KICK            = "room:kick";
  public static final String ROOM_ADD_CO_OWNER    = "room:add_co_owner";
  public static final String ROOM_REMOVE_CO_OWNER = "room:remove_co_owner";
  public static final String SPLIT_UPDATE         = "game:split";
  public static final String CHAT_MESSAGE         = "game:chat";
  public static final String ADVANCEMENT          = "game:advancement";
  public static final String PLAYER_FINISHED      = "game:finished";
  public static final String PLAYER_READY         = "game:player_ready";
  public static final String WORLD_PROGRESS       = "game:world_progress";
  public static final String LEADERBOARD_GET      = "leaderboard:get";
  public static final String PROFILE_GET          = "profile:get";
  // Server → Client
  public static final String AUTH_CHALLENGE           = "auth:challenge";
  public static final String AUTH_SUCCESS             = "auth:success";
  public static final String AUTH_FAILURE             = "auth:failure";
  public static final String QUEUE_STATUS             = "queue:status";
  public static final String MATCH_FOUND              = "queue:match_found";
  public static final String COUNTDOWN                = "game:countdown";
  public static final String READY_COUNTDOWN          = "game:ready_countdown";
  public static final String WORLD_PROGRESS_BROADCAST = "game:world_progress_broadcast";
  public static final String GAME_START               = "game:start";
  public static final String STATE_RESTORE            = "state:restore";
  public static final String SOLO_STARTED             = "solo:started";
  public static final String SOLO_FINISHED            = "solo:finished";
  public static final String SOLO_ABANDONED           = "solo:abandoned";
  public static final String SOLO_SEED_CHANGED        = "solo:seed_changed";
  public static final String SEED_CHANGE_VOTING       = "match:seed_voting";
  public static final String SEED_CHANGE_ACCEPTED     = "match:seed_accepted";
  public static final String SEED_CHANGE_EXPIRED      = "match:seed_expired";
  public static final String DRAW_VOTING              = "match:draw_voting";
  public static final String DRAW_ACCEPTED            = "match:draw_accepted";
  public static final String DRAW_EXPIRED             = "match:draw_expired";
  public static final String MATCH_RESULT             = "match:result";
  public static final String ROOM_JOINED              = "room:joined";
  public static final String ROOM_LEFT                = "room:left";
  public static final String ROOM_STATE               = "room:state";
  public static final String ROOM_ERROR               = "room:error";
  public static final String PLAYER_JOINED            = "room:player_joined";
  public static final String PLAYER_LEFT              = "room:player_left";
  public static final String PLAYER_FINISHED_BROADCAST = "room:player_finished";
  public static final String SPLIT_BROADCAST          = "game:split_broadcast";
  public static final String CHAT_BROADCAST           = "game:chat_broadcast";
  public static final String ADVANCEMENT_BROADCAST    = "game:advancement_broadcast";
  public static final String LEADERBOARD_DATA         = "leaderboard:data";
  public static final String PROFILE_DATA             = "profile:data";
  public static final String ERROR                    = "error";
}