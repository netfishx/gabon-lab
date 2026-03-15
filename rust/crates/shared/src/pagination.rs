use serde::Serialize;

/// Paginated response.
/// `{ "items": [...], "page": 1, "pageSize": 20, "total": 100 }`
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Paginated<T: Serialize> {
    pub items: Vec<T>,
    pub page: i64,
    pub page_size: i64,
    pub total: i64,
}

impl<T: Serialize> Paginated<T> {
    pub fn new(items: Vec<T>, page: i64, page_size: i64, total: i64) -> Self {
        Self {
            items,
            page,
            page_size,
            total,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn paginated_serializes_camel_case() {
        let p = Paginated::new(vec![1, 2, 3], 1, 20, 100);
        let json = serde_json::to_value(&p).unwrap();

        assert_eq!(json["items"], serde_json::json!([1, 2, 3]));
        assert_eq!(json["page"], 1);
        assert_eq!(json["pageSize"], 20); // camelCase
        assert_eq!(json["total"], 100);
    }

    #[test]
    fn paginated_empty_items() {
        let p: Paginated<String> = Paginated::new(vec![], 1, 20, 0);
        let json = serde_json::to_value(&p).unwrap();

        assert_eq!(json["items"], serde_json::json!([]));
        assert_eq!(json["total"], 0);
    }
}
