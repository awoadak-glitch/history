-- AWR MX Desktop v5 - native player mouse + keyboard interaction layer
-- Native mpv window, without intercepting the original progress bar.
-- Mouse: tap = pause; hold 320ms = temporary 2x, horizontal drag = discrete 0.5x steps.
-- Space: tap = pause; hold 320ms = temporary 2x, release = saved speed.
-- Space + Right / Left while held: optional 0.5x adjustment.

local HOLD_DELAY = 0.32
local EARLY_DRAG_PIXELS = 18
local START_SPEED = 2.0
local MIN_SPEED = 0.5
local MAX_SPEED = 4.0
local SPEED_STEP = 0.5
local POLL_INTERVAL = 0.035
local current = nil
local mouse_bound = false
local overlay = mp.create_osd_overlay('ass-events')
overlay.res_x = 1280
overlay.res_y = 720

local function clamp(value, minimum, maximum)
    return math.max(minimum, math.min(maximum, value))
end

local function mouse_info()
    local pos = mp.get_property_native('mouse-pos', {})
    if type(pos) ~= 'table' then return {x = 0, y = 0, hover = false} end
    return pos
end

local function window_size()
    local w, h = mp.get_osd_size()
    return math.max(w or 1, 1), math.max(h or 1, 1)
end

local function discrete_speed(value)
    local stepped = math.floor(value / SPEED_STEP + 0.5) * SPEED_STEP
    return clamp(stepped, MIN_SPEED, MAX_SPEED)
end

local function speed_hud(speed, source)
    local choices = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0}
    local parts = {}
    for _, choice in ipairs(choices) do
        local color = (math.abs(choice - speed) < 0.001) and '&H50D4FF&' or '&HAFC3D6&'
        local label = (math.abs(choice - speed) < 0.001) and ('[' .. string.format('%.1f', choice) .. ']') or string.format('%.1f', choice)
        table.insert(parts, '{\\1c' .. color .. '}' .. label)
    end
    local subtitle = source == 'keyboard' and 'SPACE  |  release to restore  |  Left/Right = 0.5x' or 'HOLD  |  DRAG LEFT/RIGHT = 0.5x  |  RELEASE = NORMAL'
    -- Separate ASS overlay from mpv subtitles and built-in OSC.
    overlay.data = string.format(
        '{\\an8\\pos(640,34)\\fs34\\fnSegoe UI\\b1\\bord7\\shad0\\3c&H111C2D&\\1c&HFFFFFF&}AWR MX    %.1fx' ..
        '\\N{\\fs18\\b0\\1c&H87BDD9&}%s' ..
        '\\N{\\fs22\\bord5\\3c&H111C2D&}%s',
        speed, subtitle, table.concat(parts, '   ')
    )
    overlay:update()
end

local function start_gesture(source)
    if current then return end
    local pos = mouse_info()
    current = {
        source = source,
        start_x = pos.x or 0,
        saved_speed = mp.get_property_number('speed', 1.0),
        saved_pause = mp.get_property_bool('pause', false),
        active = false,
        speed = 0,
        timer = nil
    }
    current.timer = mp.add_timeout(HOLD_DELAY, function()
        if current and current.source == source then
            current.timer = nil
            if not current.active then
                current.active = true
                current.speed = START_SPEED
                if current.saved_pause then mp.set_property_bool('pause', false) end
                mp.set_property_number('speed', START_SPEED)
                speed_hud(START_SPEED, source)
            end
        end
    end)
end

local function begin_boost()
    if not current or current.active then return end
    if current.timer then current.timer:kill(); current.timer = nil end
    current.active = true
    current.speed = START_SPEED
    if current.saved_pause then mp.set_property_bool('pause', false) end
    mp.set_property_number('speed', START_SPEED)
    speed_hud(START_SPEED, current.source)
end

local function update_speed(value)
    if not current then return end
    begin_boost()
    local next_speed = discrete_speed(value)
    if next_speed ~= current.speed then
        current.speed = next_speed
        mp.set_property_number('speed', next_speed)
        speed_hud(next_speed, current.source)
    end
end

local function update_drag()
    if not current or current.source ~= 'mouse' then return end
    local pos = mouse_info()
    local w = window_size()
    local delta = (pos.x or current.start_x) - current.start_x
    if not current.active and math.abs(delta) >= EARLY_DRAG_PIXELS then
        begin_boost()
    end
    if not current.active then return end
    -- Each gesture step is 7.5% of the player width (minimum 52 px).
    local step_pixels = clamp(w * 0.075, 52, 94)
    local steps = math.floor(math.abs(delta) / step_pixels + 0.001)
    local direction = delta < 0 and -1 or 1
    update_speed(START_SPEED + direction * steps * SPEED_STEP)
end

local function end_gesture(source, do_toggle)
    if not current then return end
    if source and current.source ~= source then return end
    local gesture = current
    current = nil
    if gesture.timer then gesture.timer:kill() end
    overlay:remove()
    if gesture.active then
        mp.set_property_number('speed', gesture.saved_speed)
        mp.set_property_bool('pause', gesture.saved_pause)
    elseif do_toggle then
        mp.commandv('cycle', 'pause')
    end
end

local function handle_mouse(event)
    if event.canceled then
        end_gesture('mouse', false)
    elseif event.event == 'down' then
        start_gesture('mouse')
    elseif event.event == 'up' or event.event == 'press' then
        end_gesture('mouse', true)
    end
end

local function handle_space(event)
    if event.canceled then
        end_gesture('keyboard', false)
    elseif event.event == 'down' then
        start_gesture('keyboard')
    elseif event.event == 'up' or event.event == 'press' then
        -- Some input adapters send a short press without separate down/up.
        if event.event == 'press' and not current then
            mp.commandv('cycle', 'pause')
        else
            end_gesture('keyboard', true)
        end
    end
    -- Ignore OS autorepeat; the hold timer handles sustained key down.
end

local function handle_keyboard_step(direction)
    if current and current.source == 'keyboard' then
        update_speed((current.active and current.speed or START_SPEED) + direction * SPEED_STEP)
    else
        -- Keep normal arrow-key seeking outside Space hold.
        mp.commandv('seek', direction * 5, 'relative')
    end
end

local function poll()
    if current and current.source == 'mouse' then
        update_drag()
        return
    end
    local pos = mouse_info()
    local _, h = window_size()
    -- Leave the bottom OSC controls and progress bar clickable.
    local reserved = clamp(h * 0.18, 100, 165)
    local video_area = pos.hover and (pos.y or 0) < h - reserved
    if video_area and not mouse_bound then
        mp.add_forced_key_binding('MBTN_LEFT', 'awr-video-pointer', handle_mouse, {complex = true})
        mouse_bound = true
    elseif not video_area and mouse_bound then
        mp.remove_key_binding('awr-video-pointer')
        mouse_bound = false
    end
end

mp.add_forced_key_binding('SPACE', 'awr-hold-space', handle_space, {complex = true, repeatable = false})
-- Bind arrows only while the Space press gesture is active, instead of overriding seeking.
local arrows_bound = false
local function update_arrows()
    local needed = current and current.source == 'keyboard'
    if needed and not arrows_bound then
        mp.add_forced_key_binding('RIGHT', 'awr-space-speed-right', function() handle_keyboard_step(1) end, {repeatable=true})
        mp.add_forced_key_binding('LEFT', 'awr-space-speed-left', function() handle_keyboard_step(-1) end, {repeatable=true})
        arrows_bound = true
    elseif not needed and arrows_bound then
        mp.remove_key_binding('awr-space-speed-right')
        mp.remove_key_binding('awr-space-speed-left')
        arrows_bound = false
    end
end
mp.add_periodic_timer(POLL_INTERVAL, function() poll(); update_arrows() end)

-- Avoid mouse click moving the window; Ctrl+left drag remains possible.
mp.set_property_number('input-dragging-deadzone', 100000)
mp.add_forced_key_binding('Ctrl+MBTN_LEFT', 'awr-window-move', function(ev)
    if ev.event == 'down' then mp.commandv('begin-vo-dragging') end
end, {complex=true})

mp.observe_property('focused', 'bool', function(_, value)
    if value == false then end_gesture(nil, false) end
end)
mp.register_event('end-file', function() end_gesture(nil, false) end)
mp.register_event('shutdown', function() end_gesture(nil, false) end)
mp.register_event('file-loaded', function()
    mp.osd_message('AWR MX  |  Hold Space or mouse = 2x  |  drag = 0.5x steps  |  release = restore', 5)
end)
