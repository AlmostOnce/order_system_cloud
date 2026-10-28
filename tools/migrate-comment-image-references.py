"""Read-only by default; migrate Mongo comment images to imageFileKeys after stopping comment-service."""
from pathlib import Path
from datetime import datetime
import argparse
import json
import socket
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    env = {}
    for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            env[key.strip()] = value.strip().strip('\"\'')
    prefix = ['wsl', '-d', 'Ubuntu-24.04', '--', 'docker', 'exec', '-i',
              '-e', 'MIGRATION_USER=' + env['COMMENT_MONGO_USERNAME'],
              '-e', 'MIGRATION_PASSWORD=' + env['COMMENT_MONGO_PASSWORD'],
              'order-system-cloud-mongo-1', 'mongosh', '--quiet', '--nodb', '--file', '/dev/stdin']
    connect = '''
const c = new Mongo('mongodb://127.0.0.1:27017');
const d = c.getDB('comment_db');
if (!d.auth(process.env.MIGRATION_USER, process.env.MIGRATION_PASSWORD)) throw Error('Authentication failed');
'''
    validate = r'''
function keys(document) {
  if (document.images !== undefined && !Array.isArray(document.images)) throw Error('Invalid legacy images');
  const old = document.images === undefined ? null : document.images.map(value => {
    if (typeof value !== 'string') throw Error('Invalid legacy image URL');
    const match = value.match(/^(?:https?:\/\/[^/?#]+)?\/api\/files\/([a-fA-F0-9]{32})\/content$/);
    if (!match) throw Error('Unknown legacy image URL: import into file service before migrating');
    return match[1].toLowerCase();
  });
  const current = document.imageFileKeys;
  if (current !== undefined && (!Array.isArray(current) || current.some(k => typeof k !== 'string' || !/^[a-f0-9]{32}$/.test(k)))) throw Error('Invalid imageFileKeys');
  if (old !== null && current !== undefined && JSON.stringify(old) !== JSON.stringify(current)) throw Error('Conflicting old/new image references');
  if (old === null && current === undefined) throw Error('Missing image reference fields');
  return current === undefined ? old : current;
}
const documents = d.comments.find({}).toArray();
documents.forEach(keys);
'''

    def execute(source):
        result = subprocess.run(prefix, input=(connect + source).encode(), capture_output=True)
        if result.returncode:
            raise RuntimeError('Mongo migration failed; credentials and document contents omitted')
        return result.stdout.decode().strip()

    result = json.loads(execute(validate + "print(JSON.stringify({documents:documents.length, legacy:documents.filter(x=>x.images!==undefined).length}));"))
    print('Mongo preflight:', result)
    if not args.apply:
        print('No Mongo data changed.')
        return
    with socket.socket() as probe:
        probe.settimeout(1)
        assert probe.connect_ex(('127.0.0.1', 9105)) != 0, 'Stop comment-service before migration'
    backup = Path.home() / '.codex/backups/order_system_cloud' / ('comment_images_' + datetime.now().strftime('%Y%m%d_%H%M%S') + '.ejson')
    backup.parent.mkdir(parents=True, exist_ok=True)
    backup.write_text(execute(validate + 'print(EJSON.stringify(documents, null, 2, {relaxed:false}));'), encoding='utf-8')
    print(execute(validate + '''
for (const document of documents) {
  const result = d.comments.updateOne({_id:document._id}, {$set:{imageFileKeys:keys(document)}, $unset:{images:''}});
  if (result.matchedCount !== 1) throw Error('Document changed during migration');
}
if (d.comments.countDocuments({images:{$exists:true}}) !== 0) throw Error('Legacy images remain');
print('Mongo image references migrated: '+documents.length);
'''))
    print('Backup:', backup)

if __name__ == '__main__':
    main()
