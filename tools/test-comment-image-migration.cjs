// Run the migration's actual JavaScript conversion without connecting to MongoDB.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync(path.join(__dirname, 'migrate-comment-image-references.py'), 'utf8');
const script = source.match(/validate = r'''([\s\S]*?)'''/)[1].split('const documents =')[0];
const context = vm.createContext({});
vm.runInContext(script, context);
const key = '0123456789abcdef0123456789abcdef';
const convert = document => Array.from(context.keys(document));
assert.deepEqual(convert({ images: ['/api/files/' + key.toUpperCase() + '/content'] }), [key]);
assert.deepEqual(convert({ images: ['http://localhost:9090/api/files/' + key + '/content'] }), [key]);
assert.deepEqual(convert({ imageFileKeys: [key] }), [key]);
assert.deepEqual(convert({ images: [], imageFileKeys: [] }), []);
for (const document of [{ images: null }, {}, { images: ['https://example.test/photo.jpg'] },
    { imageFileKeys: ['invalid'] }, { images: [], imageFileKeys: [key] }]) {
    assert.throws(() => convert(document));
}
console.log('Mongo image migration conversion passed: managed URLs, normalized keys, reruns, conflicts and unknown URL rejection.');
