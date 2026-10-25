#pragma once

#include <vector>
#include <string>
#include <cstdint>
#include "tree_sitter/api.h"

class CppHighlighter {
public:
    CppHighlighter();
    ~CppHighlighter();

    // Returns a flat vector of [start_offset, end_offset, token_type, ...]
    // where offsets are UTF-16 code unit indices in the Kotlin string.
    std::vector<int32_t> highlight(const char* utf8_text, size_t length);

private:
    TSParser* parser;
    TSQuery* query;
    TSTree* old_tree;
};
