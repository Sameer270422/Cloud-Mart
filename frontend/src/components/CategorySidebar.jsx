import React, { useState } from 'react';

// Categories that have no subcategories yet just render as a plain link -
// only categories with something to expand into get a toggle at all.
export default function CategorySidebar({ tree, selectedCategory, selectedSubcategory, onSelect }) {
  const [expanded, setExpanded] = useState(() => new Set(selectedCategory ? [selectedCategory] : []));

  const toggle = (category) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(category)) next.delete(category); else next.add(category);
      return next;
    });
  };

  return (
    <nav className="category-sidebar" aria-label="Product categories">
      <button
        className={`category-link ${!selectedCategory ? 'active' : ''}`}
        onClick={() => onSelect(null, null)}
      >
        All products
      </button>
      {tree.map((node) => {
        const isOpen = expanded.has(node.category) || selectedCategory === node.category;
        const isActiveCategory = selectedCategory === node.category && !selectedSubcategory;
        return (
          <div className="category-group" key={node.category}>
            <div className="category-row">
              <button
                className={`category-link ${isActiveCategory ? 'active' : ''}`}
                onClick={() => onSelect(node.category, null)}
              >
                {node.category}
              </button>
              {node.subcategories.length > 0 && (
                <button
                  type="button"
                  className="category-toggle"
                  onClick={() => toggle(node.category)}
                  aria-label={isOpen ? `Collapse ${node.category}` : `Expand ${node.category}`}
                  aria-expanded={isOpen}
                >
                  {isOpen ? '−' : '+'}
                </button>
              )}
            </div>
            {isOpen && node.subcategories.length > 0 && (
              <div className="subcategory-list">
                {node.subcategories.map((sub) => (
                  <button
                    key={sub}
                    className={`subcategory-link ${selectedCategory === node.category && selectedSubcategory === sub ? 'active' : ''}`}
                    onClick={() => onSelect(node.category, sub)}
                  >
                    {sub}
                  </button>
                ))}
              </div>
            )}
          </div>
        );
      })}
    </nav>
  );
}
