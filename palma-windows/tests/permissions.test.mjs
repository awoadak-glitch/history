import test from 'node:test';
import assert from 'node:assert/strict';
import {allowAppPermission} from '../src/permissions.mjs';
test('fullscreen is allowed for the bundled main frame, never remote pages or unrelated permissions',()=>{
 assert.equal(allowAppPermission('fullscreen','palma://app/'),true);
 for(const url of ['https://example.com','palma://other/','palma://app.example/'])assert.equal(allowAppPermission('fullscreen',url),false);
 assert.equal(allowAppPermission('fullscreen','palma://app/',false),false);
 for(const permission of ['camera','microphone','media','notifications','clipboard-read'])assert.equal(allowAppPermission(permission,'palma://app/'),false);
});
