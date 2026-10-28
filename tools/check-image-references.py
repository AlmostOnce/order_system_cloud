"""Static contract audit, not Java compilation or runtime authorization testing."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
for module, model, column in [('user-service','UserDO','avatar_file_key'),
                             ('product-service','ProductDO','image_file_key'),
                             ('product-service','DiningWindowDO','image_file_key'),
                             ('product-service','RestaurantDO','image_file_key')]:
    path = next((root / module / 'src/main/java').rglob(model + '.java'))
    assert '"' + column + '"' in path.read_text(encoding='utf-8'), str(path)
for module in ['user-service','product-service','comment-service']:
    for path in (root / module / 'src/main/java').rglob('*.java'):
        source = path.read_text(encoding='utf-8')
        assert '"image_url"' not in source and '"avatar_url"' not in source, str(path)
        assert '.requireImage(' not in source, str(path)
content = next((root / 'comment-service/src/main/java').rglob('CommentContent.java')).read_text()
assert 'List<String> imageFileKeys' in content
for name in ['ProductSaveRequest','WindowSaveRequest','RestaurantSaveRequest']:
    source = next((root / 'product-service/src/main/java').rglob(name + '.java')).read_text(encoding='utf-8')
    assert 'private String imageFileKey;' in source and 'private String imageUrl;' not in source
print('Image reference static contracts passed (not a Java runtime test).')
