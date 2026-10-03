import SwiftUI

/// Lays out a screen's two parts for any orientation.
/// Portrait: `main` on top, `side` below (capped width on iPad).
/// Landscape: `main` on the left, `side` on the right, scrolling if it doesn't fit.
struct AdaptiveSplit<Main: View, Side: View>: View {
    @ViewBuilder var main: Main
    @ViewBuilder var side: Side

    private let maxSideWidth: CGFloat = 520

    var body: some View {
        GeometryReader { geo in
            if geo.size.width > geo.size.height {
                HStack(spacing: 0) {
                    main.frame(maxWidth: .infinity, maxHeight: .infinity)
                    ScrollView {
                        side
                            .frame(minHeight: geo.size.height)
                    }
                    .scrollBounceBehavior(.basedOnSize)
                    .frame(width: min(maxSideWidth, geo.size.width * 0.45))
                }
            } else {
                VStack(spacing: 0) {
                    main.frame(maxHeight: .infinity)
                    // Only as tall as its content, so spacers inside it collapse.
                    side.frame(maxWidth: maxSideWidth).fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
}
