-- AWR MX v5 deterministic gesture regression test with a mock mpv API.
local source_script = assert(arg and arg[1], 'missing player Lua path')
local props = {speed = 1.0, pause = false, focused = true}
local mouse = {x = 200, y = 200, hover = true}
local bindings, observers = {}, {}
local periodic, timers, messages = {}, {}, {}
local overlay = {data = ''}
function overlay:update() end
function overlay:remove() self.data = '' end
mp = {
  create_osd_overlay = function() return overlay end,
  get_property_native = function(key) if key == 'mouse-pos' then return mouse end return props[key] end,
  get_osd_size = function() return 1000, 600 end,
  get_property_number = function(k, fallback) if props[k]==nil then return fallback else return props[k] end end,
  get_property_bool = function(k, fallback) if props[k]==nil then return fallback else return props[k] end end,
  set_property_number = function(k, val) props[k] = val end,
  set_property_bool = function(k, val) props[k] = val end,
  commandv = function(k, v) if k=='cycle' and v=='pause' then props.pause = not props.pause end end,
  add_forced_key_binding = function(_, name, callback) bindings[name] = callback end,
  remove_key_binding = function(name) bindings[name] = nil end,
  observe_property = function(key, _, callback) observers[key] = callback end,
  register_event = function(name, callback) messages[name] = callback end,
  add_periodic_timer = function(_, callback) table.insert(periodic, callback) end,
  add_timeout = function(_, callback)
    local t = {killed=false, callback=callback}
    function t:kill() self.killed = true end
    table.insert(timers, t)
    return t
  end,
  osd_message = function() end,
}
local function poll() for _, cb in ipairs(periodic) do cb() end end
local function tick_timer() local t = assert(timers[#timers]); assert(not t.killed); t.callback() end
local function eq(actual, expected, name)
  assert(actual == expected, name .. ': expected ' .. tostring(expected) .. ', got ' .. tostring(actual))
end
local function near(actual, expected, name)
  assert(math.abs(actual-expected) < 1e-9, name .. ': expected ' .. tostring(expected) .. ' got ' .. tostring(actual))
end
local function mouseDown()
  poll(); assert(bindings['awr-video-pointer'], 'mouse gesture bound')
  bindings['awr-video-pointer']({event='down'})
end
local function mouseUp()
  bindings['awr-video-pointer']({event='up'}); poll()
end
local function mouseDrag(offset)
  mouse.x = 200 + offset
  poll()
end

dofile(source_script)
assert(bindings['awr-hold-space'], 'Space long-press handler is installed')
-- Keyboard short tap toggles pause without changing playback speed.
local space = bindings['awr-hold-space']
space({event='down'}); space({event='up'})
eq(props.pause, true, 'short Space toggles pause')
near(props.speed, 1, 'short Space leaves speed unchanged')
space({event='down'})
tick_timer()
near(props.speed, 2, 'long Space becomes temporary 2x')
eq(props.pause, false, 'long Space unpauses during boost')
assert(overlay.data:find('2.0x', 1, true), 'speed badge displayed')
space({event='up'})
near(props.speed, 1, 'space release restores original speed')
eq(props.pause, true, 'space release restores original pause state')
-- Holding Space + arrow keys moves one discrete 0.5x step.
props.pause = false
props.speed = 1.5
space({event='down'}); poll()
assert(bindings['awr-space-speed-right'], 'Space + right key bound')
bindings['awr-space-speed-right']()
near(props.speed, 2.5, 'Space+right increments 0.5 from temporary 2x')
bindings['awr-space-speed-left']()
near(props.speed, 2.0, 'Space+left decrements 0.5')
space({event='up'}); poll()
near(props.speed, 1.5, 'restores custom speed')
assert(not bindings['awr-space-speed-right'], 'Arrow override removed after Space release')
-- Mouse short click pauses. Long hold starts at 2x; horizontal drag is discrete.
props.speed=1.0; props.pause=false; mouse.x=200; mouse.y=200
mouseDown(); mouseUp()
eq(props.pause, true, 'short click toggles pause')
props.pause=false
mouseDown(); tick_timer()
near(props.speed, 2, 'long click 2x')
mouseDrag(74); near(props.speed,2,'74px still at 2x step')
mouseDrag(75); near(props.speed,2.5,'75px 2.5x')
mouseDrag(150); near(props.speed,3,'150px 3x')
mouseDrag(225); near(props.speed,3.5,'225px 3.5x')
mouseDrag(300); near(props.speed,4,'300px reaches 4x')
mouseDrag(900); near(props.speed,4,'speed capped 4x')
mouseDrag(0); near(props.speed,2,'back to start returns 2x')
mouseDrag(-75); near(props.speed,1.5,'drag left drops to 1.5x')
mouseDrag(-150); near(props.speed,1,'drag left drops to 1x')
mouseDrag(-225); near(props.speed,.5,'drag left drops to 0.5x')
mouseDrag(-700); near(props.speed,.5,'speed floor 0.5x')
mouseUp(); near(props.speed,1,'release restores 1x')
-- Swipe triggers gesture quickly (without waiting for hold timer).
mouse.x=200; mouseDown(); mouseDrag(35)
near(props.speed,2,'early drag activates temporary boost')
mouseUp(); near(props.speed,1,'early drag release resets')
-- Focus loss / video end restore speed without pause toggle.
props.speed=1.25
mouse.x=200; mouseDown(); tick_timer(); observers.focused('', false)
near(props.speed,1.25,'focus lost restores the saved speed')
mouse.x=200; mouseDown(); tick_timer(); messages['end-file']()
near(props.speed,1.25,'end of file restores the saved speed')
-- Native controller remains free at bottom.
mouse.y=590; poll()
assert(bindings['awr-video-pointer']==nil, 'bottom OSC receives mouse events')
mouse.y=200; poll()
assert(bindings['awr-video-pointer'], 'pointer binding resumes in video region')
print('PASS: Space press, discrete dragging, custom restore, native OSC isolation, focus/end cancellation')
