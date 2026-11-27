-- USER-003: tìm người để mời theo tên — khớp đầu từ, không phân biệt hoa/thường và dấu tiếng Việt.
-- Không bảng / cột mới, không extension: hai hàm IMMUTABLE + một index GIN biểu thức trên "user".display_name.

-- Chuẩn hoá tên để tìm: NFC → chữ thường → bỏ dấu tiếng Việt → ký tự không phải [a-z0-9] thành một dấu cách.
-- Nguồn duy nhất của bảng ký tự: index, câu tìm và BE (SELECT user_name_fold(:q)) cùng gọi hàm này.
-- Sửa bảng ký tự sau này: migration mới CREATE OR REPLACE FUNCTION và REINDEX INDEX idx_user_name_search.
CREATE FUNCTION user_name_fold(name text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$
SELECT btrim(regexp_replace(
           translate(lower(normalize(name, NFC)),
                     'àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ',
                     'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd'),
           '[^a-z0-9]+', ' ', 'g'))
$$;

CREATE FUNCTION user_name_search_vector(name text) RETURNS tsvector
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$
SELECT to_tsvector('simple'::regconfig, user_name_fold(name))
$$;

CREATE INDEX idx_user_name_search ON "user"
    USING GIN (user_name_search_vector(display_name))
    WHERE deleted_at IS NULL;
