import Foundation

/// 500 → "500", 1500 → "1.5K", 12000 → "12K".
func short(_ n: Int) -> String {
    n < 1000 ? "\(n)" : (Double(n) / 1000).formatted(.number.precision(.fractionLength(0...1))) + "K"
}
