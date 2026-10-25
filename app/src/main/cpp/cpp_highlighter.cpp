#include "cpp_highlighter.hpp"
#include <algorithm>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "CppHighlighter"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" const TSLanguage *tree_sitter_cpp(void);

// Tree-sitter C++ highlight query
// Token Types:
// 1: Keyword (e.g. #include, using, int, return)
// 2: Type (e.g. std, string, vector)
// 3: String Literal (e.g. "...", '<...>')
// 4: Comment (e.g. // ..., /* ... */)
// 5: Function / Operator (e.g. cout, <<, endl)
// 6: Number Literal (e.g. 123, 0xAA, 3.14)
static const char* CPP_HIGHLIGHT_QUERY =
    "\"break\" @keyword\n"
    "\"case\" @keyword\n"
    "\"const\" @keyword\n"
    "\"continue\" @keyword\n"
    "\"default\" @keyword\n"
    "\"do\" @keyword\n"
    "\"else\" @keyword\n"
    "\"enum\" @keyword\n"
    "\"extern\" @keyword\n"
    "\"for\" @keyword\n"
    "\"if\" @keyword\n"
    "\"inline\" @keyword\n"
    "\"return\" @keyword\n"
    "\"sizeof\" @keyword\n"
    "\"static\" @keyword\n"
    "\"struct\" @keyword\n"
    "\"switch\" @keyword\n"
    "\"typedef\" @keyword\n"
    "\"union\" @keyword\n"
    "\"volatile\" @keyword\n"
    "\"while\" @keyword\n"
    "\"#define\" @keyword\n"
    "\"#elif\" @keyword\n"
    "\"#else\" @keyword\n"
    "\"#endif\" @keyword\n"
    "\"#if\" @keyword\n"
    "\"#ifdef\" @keyword\n"
    "\"#ifndef\" @keyword\n"
    "\"#include\" @keyword\n"
    "(preproc_directive) @keyword\n"
    "[\n"
    " \"catch\"\n"
    " \"class\"\n"
    " \"co_await\"\n"
    " \"co_return\"\n"
    " \"co_yield\"\n"
    " \"constexpr\"\n"
    " \"constinit\"\n"
    " \"consteval\"\n"
    " \"delete\"\n"
    " \"explicit\"\n"
    " \"final\"\n"
    " \"friend\"\n"
    " \"mutable\"\n"
    " \"namespace\"\n"
    " \"noexcept\"\n"
    " \"new\"\n"
    " \"override\"\n"
    " \"private\"\n"
    " \"protected\"\n"
    " \"public\"\n"
    " \"template\"\n"
    " \"throw\"\n"
    " \"try\"\n"
    " \"typename\"\n"
    " \"using\"\n"
    " \"concept\"\n"
    " \"requires\"\n"
    " \"virtual\"\n"
    " \"nullptr\"\n"
    "] @keyword\n"
    "(true) @keyword\n"
    "(false) @keyword\n"
    "(this) @keyword\n"
    "((identifier) @type (#match? @type \"^(std|string|vector|map|set|pair|tuple|unique_ptr|shared_ptr)$\"))\n"
    "((identifier) @function (#match? @function \"^(cout|cin|cerr|endl)$\"))\n"
    "(primitive_type) @keyword\n"
    "(type_identifier) @type\n"
    "(sized_type_specifier) @type\n"
    "(namespace_identifier) @type\n"
    "(auto) @type\n"
    "(string_literal) @string\n"
    "(system_lib_string) @string\n"
    "(raw_string_literal) @string\n"
    "(char_literal) @string\n"
    "(comment) @comment\n"
    "(number_literal) @number\n"
    "[\n"
    " \"<<\"\n"
    " \">>\"\n"
    " \"<=\"\n"
    " \">=\"\n"
    " \"==\"\n"
    " \"!=\"\n"
    " \"&&\"\n"
    " \"||\"\n"
    " \"->\"\n"
    " \"::\"\n"
    " \"+\"\n"
    " \"-\"\n"
    " \"*\"\n"
    " \"/\"\n"
    " \"%\"\n"
    " \"=\"\n"
    "] @operator\n"
    "(call_expression function: (identifier) @function)\n"
    "(call_expression function: (field_expression field: (field_identifier) @function))\n"
    "(call_expression function: (qualified_identifier name: (identifier) @function))\n"
    "(function_declarator declarator: (identifier) @function)\n"
    "(function_declarator declarator: (qualified_identifier name: (identifier) @function))\n"
    "(function_declarator declarator: (field_identifier) @function)\n"
    "(template_function name: (identifier) @function)\n"
    "(template_method name: (field_identifier) @function)\n"
    "(null) @keyword\n";

CppHighlighter::CppHighlighter() : parser(nullptr), query(nullptr), old_tree(nullptr) {
    parser = ts_parser_new();
    ts_parser_set_language(parser, tree_sitter_cpp());

    uint32_t error_offset;
    TSQueryError error_type;
    query = ts_query_new(tree_sitter_cpp(), CPP_HIGHLIGHT_QUERY, strlen(CPP_HIGHLIGHT_QUERY), &error_offset, &error_type);
    if (!query) {
        LOGE("Failed to compile C++ query: error %d at offset %u", error_type, error_offset);
    }
}

CppHighlighter::~CppHighlighter() {
    if (query) ts_query_delete(query);
    if (old_tree) ts_tree_delete(old_tree);
    if (parser) ts_parser_delete(parser);
}

static inline int32_t capture_to_type(const char* name, uint32_t len) {
    if (strncmp(name, "keyword", len) == 0) return 1;
    if (strncmp(name, "type", len) == 0) return 2;
    if (strncmp(name, "string", len) == 0) return 3;
    if (strncmp(name, "comment", len) == 0) return 4;
    if (strncmp(name, "function", len) == 0 || strncmp(name, "operator", len) == 0) return 5;
    if (strncmp(name, "number", len) == 0) return 6;
    return 0;
}

std::vector<int32_t> CppHighlighter::highlight(const char* utf8_text, size_t length) {
    std::vector<int32_t> tokens;
    if (!parser || !query || length == 0) {
        return tokens;
    }

    // 1. Build UTF-8 byte offset to UTF-16 code unit mapping if text contains non-ASCII characters
    bool has_non_ascii = false;
    for (size_t i = 0; i < length; ++i) {
        if ((unsigned char)utf8_text[i] >= 0x80) {
            has_non_ascii = true;
            break;
        }
    }

    std::vector<int32_t> byte_to_char;
    if (has_non_ascii) {
        byte_to_char.resize(length + 1);
        size_t b = 0;
        int32_t c = 0;
        while (b < length) {
            byte_to_char[b] = c;
            unsigned char ch = (unsigned char)utf8_text[b];
            if (ch < 0x80) {
                b += 1;
                c += 1;
            } else if ((ch & 0xE0) == 0xC0) {
                if (b + 1 <= length) byte_to_char[b + 1] = c;
                b += 2;
                c += 1;
            } else if ((ch & 0xF0) == 0xE0) {
                if (b + 1 <= length) byte_to_char[b + 1] = c;
                if (b + 2 <= length) byte_to_char[b + 2] = c;
                b += 3;
                c += 1;
            } else if ((ch & 0xF8) == 0xF0) {
                if (b + 1 <= length) byte_to_char[b + 1] = c;
                if (b + 2 <= length) byte_to_char[b + 2] = c;
                if (b + 3 <= length) byte_to_char[b + 3] = c;
                b += 4;
                c += 2; // UTF-16 surrogate pair
            } else {
                b += 1;
                c += 1;
            }
        }
        byte_to_char[length] = c;
    }

    // 2. Parse string into syntax tree (inherently error tolerant)
    TSTree* new_tree = ts_parser_parse_string(parser, old_tree, utf8_text, length);
    if (!new_tree) {
        return tokens;
    }
    if (old_tree) {
        ts_tree_delete(old_tree);
    }
    old_tree = new_tree;

    TSNode root = ts_tree_root_node(new_tree);

    // 3. Execute query
    TSQueryCursor* cursor = ts_query_cursor_new();
    ts_query_cursor_exec(cursor, query, root);

    TSQueryMatch match;
    tokens.reserve(256 * 3);

    while (ts_query_cursor_next_match(cursor, &match)) {
        for (uint16_t i = 0; i < match.capture_count; ++i) {
            uint32_t name_len;
            const char* name = ts_query_capture_name_for_id(query, match.captures[i].index, &name_len);
            int32_t type = capture_to_type(name, name_len);
            if (type <= 0) continue;

            TSNode node = match.captures[i].node;
            uint32_t start_byte = ts_node_start_byte(node);
            uint32_t end_byte = ts_node_end_byte(node);
            if (start_byte >= end_byte || start_byte > length || end_byte > length) continue;

            int32_t start_char = has_non_ascii ? byte_to_char[start_byte] : (int32_t)start_byte;
            int32_t end_char = has_non_ascii ? byte_to_char[end_byte] : (int32_t)end_byte;

            if (start_char < end_char) {
                tokens.push_back(start_char);
                tokens.push_back(end_char);
                tokens.push_back(type);
            }
        }
    }

    ts_query_cursor_delete(cursor);
    return tokens;
}
