(ns status-im.ui.screens.wallet.send.views
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [re-frame.core :as re-frame]
            [status-im.i18n :as i18n]
            [status-im.ui.components.animation :as animation]
            [status-im.ui.components.bottom-buttons.view :as bottom-buttons]
            [status-im.ui.components.button.view :as button]
            [status-im.ui.components.common.common :as common]
            [status-im.ui.components.icons.vector-icons :as vector-icons]
            [status-im.ui.components.react :as react]
            [status-im.ui.components.status-bar.view :as status-bar]
            [status-im.ui.components.styles :as components.styles]
            [status-im.ui.components.toolbar.actions :as act]
            [status-im.ui.components.toolbar.view :as toolbar]
            [status-im.ui.components.tooltip.views :as tooltip]
            [status-im.ui.screens.wallet.components.styles :as wallet.components.styles]
            [status-im.ui.screens.wallet.components.views :as components]
            [status-im.ui.screens.wallet.components.views :as wallet.components]
            [status-im.ui.screens.wallet.send.animations :as send.animations]
            [status-im.ui.screens.wallet.send.styles :as styles]
            [status-im.ui.screens.wallet.styles :as wallet.styles]
            [status-im.utils.money :as money]
            [status-im.utils.security :as security]
            [status-im.utils.utils :as utils]
            [status-im.utils.ethereum.tokens :as tokens]
            [status-im.utils.ethereum.core :as ethereum]
            [status-im.transport.utils :as transport.utils]
            [taoensso.timbre :as log]
            [reagent.core :as reagent]))

(defn- toolbar [modal? title]
  (let [action (if modal? act/close-white act/back-white)]
    [toolbar/toolbar {:style wallet.styles/toolbar}
     [toolbar/nav-button (action (if modal?
                                   #(re-frame/dispatch [:wallet/discard-transaction-navigate-back])
                                   #(act/default-handler)))]
     [toolbar/content-title {:color :white} title]]))

(defn- advanced-cartouche [{:keys [max-fee gas gas-price]}]
  [react/view
   [wallet.components/cartouche {:on-press  #(do (re-frame/dispatch [:wallet.send/clear-gas])
                                                 (re-frame/dispatch [:navigate-to-modal :wallet-transaction-fee]))}
    (i18n/label :t/wallet-transaction-fee)
    [react/view {:style               styles/advanced-options-text-wrapper
                 :accessibility-label :transaction-fee-button}
     [react/text {:style styles/advanced-fees-text}
      (str max-fee  " " (i18n/label :t/eth))]
     [react/text {:style styles/advanced-fees-details-text}
      (str (money/to-fixed gas) " * " (money/to-fixed (money/wei-> :gwei gas-price)) (i18n/label :t/gwei))]]]])

(defn- advanced-options [advanced? transaction scroll]
  [react/view {:style styles/advanced-wrapper}
   [react/touchable-highlight {:on-press (fn []
                                           (re-frame/dispatch [:wallet.send/toggle-advanced (not advanced?)])
                                           (when (and scroll @scroll) (utils/set-timeout #(.scrollToEnd @scroll) 350)))}
    [react/view {:style styles/advanced-button-wrapper}
     [react/view {:style               styles/advanced-button
                  :accessibility-label :advanced-button}
      [react/i18n-text {:style (merge wallet.components.styles/label
                                      styles/advanced-label)
                        :key   :wallet-advanced}]
      [vector-icons/icon (if advanced? :icons/up :icons/down) {:color :white}]]]]
   (when advanced?
     [advanced-cartouche transaction])])

;; "Cancel" and "Sign Transaction >" or "Sign >" buttons, signing with password
(defn enter-password-buttons [spinning? sign-handler]
  [react/view {:flex 1}
   [button/secondary-button {:style              styles/password-button
                             :on-press            sign-handler
                             :disabled?           spinning?
                             :accessibility-label :sign-transaction-button}
    (i18n/label :t/command-button-send)]])

(defview password-input-panel [transaction spinning?]
  (letsubs [account         [:get-current-account]
            wrong-password? [:wallet.send/wrong-password?]
            signing-phrase  (:signing-phrase @account)
            bottom-value    (animation/create-value -250)
            opacity-value   (animation/create-value 0)]
    {:component-did-mount #(send.animations/animate-sign-panel opacity-value bottom-value)}
    [react/animated-view {:style (styles/animated-sign-panel bottom-value)}
     [react/animated-view {:style (styles/sign-panel opacity-value)}
      [react/view styles/spinner-container
       (when spinning?
         [react/activity-indicator {:animating true
                                    :size      :large}])]
      [react/view {:style {:flex-direction :column
                           :align-items :center
                           :justify-content :center
                           :padding-horizontal 15}}
       [react/view styles/signing-phrase-container
        [react/text {:style               styles/signing-phrase
                     :accessibility-label :signing-phrase-text}
         signing-phrase]]
       (when (:symbol transaction)
         [react/text {:style styles/transaction-amount}
          (str "Send " (:amount-text transaction) " " (name (:symbol transaction)))])
       [react/view {:style                       styles/password-container
                    :important-for-accessibility :no-hide-descendants}
        [react/text-input
         {:auto-focus             true
          :secure-text-entry      true
          :placeholder            "Enter your login password..." #_(i18n/label :t/enter-password)
          :placeholder-text-color components.styles/color-gray4
          :on-change-text         #(re-frame/dispatch [:wallet.send/set-password (security/mask-data %)])
          :style                  styles/password
          :accessibility-label    :enter-password-input
          :auto-capitalize        :none}]]
       [enter-password-buttons spinning?
        #(re-frame/dispatch [:wallet/cancel-entering-password])
        #(re-frame/dispatch [:wallet/send-transaction])]]]
     [tooltip/tooltip "Only send the transaction if you recognize your three emoji's" styles/emojis-tooltip]]))

;; "Sign Transaction >" button
(defn- sign-transaction-button [amount-error to amount sufficient-funds? sufficient-gas? modal?]
  (let [sign-enabled? (and (nil? amount-error)
                           (or modal? (not (empty? to))) ;;NOTE(goranjovic) - contract creation will have empty `to`
                           (not (nil? amount))
                           sufficient-funds?
                           sufficient-gas?)]
    [bottom-buttons/bottom-buttons
     styles/sign-buttons
     [react/view]
     [button/button {:style               components.styles/flex
                     :disabled?           (not sign-enabled?)
                     :on-press            #(re-frame/dispatch [:set-in
                                                               [:wallet :send-transaction :show-password-input?]
                                                               true])
                     :text-style          {:color :white}
                     :accessibility-label :sign-transaction-button}
      (i18n/label :t/transactions-sign-transaction)
      [vector-icons/icon :icons/forward {:color (if sign-enabled? :white :gray)}]]]))

(defn opacify-background []
  [react/view {:flex 1
               :background-color :black
               :opacity          0.5
               :position :absolute
               :top 0
               :left 0
               :right 0
               :bottom 0
               :z-index 2}])

(defn- render-send-transaction-view [{:keys [modal? transaction scroll advanced? network amount-input]}]
  (let [{:keys [amount amount-text amount-error asset-error show-password-input? to to-name sufficient-funds?
                sufficient-gas? in-progress? from-chat? symbol]} transaction
        {:keys [decimals] :as token} (tokens/asset-for (ethereum/network->chain-keyword network) symbol)]
    [react/view {:flex 1
                 :flex-direction :row}

     [(if modal?
        react/view
        react/keyboard-avoiding-view) styles/send-transaction-form-container
      [status-bar/status-bar {:type (if modal? :modal-wallet :wallet)}]
      [toolbar modal? (i18n/label :t/send-transaction)]
      [react/view components.styles/flex
       [common/network-info {:text-color :white}]
       [react/scroll-view {:keyboard-should-persist-taps :always
                           :ref                          #(reset! scroll %)
                           :on-content-size-change       #(when (and (not modal?) scroll @scroll)
                                                            (.scrollToEnd @scroll))}
        [react/view styles/send-transaction-form
         [components/recipient-selector {:disabled? (or from-chat? modal?)
                                         :address   to
                                         :name      to-name
                                         :modal?    modal?}]
         [components/asset-selector {:disabled? (or from-chat? modal?)
                                     :error     asset-error
                                     :type      :send
                                     :symbol    symbol}]
         [components/amount-selector {:disabled?     (or from-chat? modal?)
                                      :error         (or amount-error
                                                         (when-not sufficient-funds? (i18n/label :t/wallet-insufficient-funds))
                                                         (when-not sufficient-gas? (i18n/label :t/wallet-insufficient-gas)))
                                      :amount        amount
                                      :amount-text   amount-text
                                      :input-options {:on-change-text #(re-frame/dispatch [:wallet.send/set-and-validate-amount % symbol decimals])
                                                      :ref            (partial reset! amount-input)}} token]
         [advanced-options advanced? transaction scroll]]]
       [sign-transaction-button amount-error to amount sufficient-funds? sufficient-gas? modal?]]]
     (when show-password-input?
       [opacify-background])
     (when show-password-input?
       [password-input-panel transaction in-progress?])
     (when in-progress? [react/view styles/processing-view])]))

;; MAIN SEND TRANSACTION VIEW
(defn- send-transaction-view [{:keys [scroll] :as opts}]
  (let [amount-input (atom nil)
        handler      (fn [_]
                       (when (and scroll @scroll @amount-input
                                  (.isFocused @amount-input))
                         (log/debug "Amount field focused, scrolling down")
                         (.scrollToEnd @scroll)))]
    (reagent/create-class
     {:component-will-mount (fn [_]
                              ;;NOTE(goranjovic): keyboardDidShow is for android and keyboardWillShow for ios
                              (.addListener react/keyboard "keyboardDidShow" handler)
                              (.addListener react/keyboard "keyboardWillShow" handler))
      :reagent-render       (fn [opts] (render-send-transaction-view
                                        (assoc opts :amount-input amount-input)))})))

;; SEND TRANSACTION FROM WALLET (CHAT)
(defview send-transaction []
  (letsubs [transaction [:wallet.send/transaction]
            advanced?   [:wallet.send/advanced?]
            network     [:get-current-account-network]
            scroll      (atom nil)]
    [send-transaction-view {:modal? false :transaction transaction :scroll scroll :advanced? advanced?
                            :network network}]))

;; SEND TRANSACTION FROM DAPP
(defview send-transaction-modal []
  (letsubs [transaction [:wallet.send/transaction]
            advanced?   [:wallet.send/advanced?]
            network     [:get-current-account-network]
            scroll      (atom nil)]
    (if transaction
      [send-transaction-view {:modal? true :transaction transaction :scroll scroll :advanced? advanced?
                              :network network}]
      [react/view wallet.styles/wallet-modal-container
       [react/view components.styles/flex
        [status-bar/status-bar {:type :modal-wallet}]
        [toolbar true (i18n/label :t/send-transaction)]
        [react/i18n-text {:style styles/empty-text
                          :key   :unsigned-transaction-expired}]]])))

(defview modal-enter-password-buttons [spinning? cancel-handler sign-handler sign-label]
  [bottom-buttons/bottom-buttons
   styles/sign-buttons
   [button/button {:style               components.styles/flex
                   :on-press            cancel-handler
                   :accessibility-label :cancel-button}
    (i18n/label :t/cancel)]
   [button/button {:style               (wallet.styles/button-container true)
                   :on-press            sign-handler
                   :disabled?           spinning?
                   :accessibility-label :sign-transaction-button}
    (i18n/label sign-label)
    [vector-icons/icon :icons/forward {:color :white}]]])

;; SIGN MESSAGE FROM DAPP
(defview sign-message-modal []
  (letsubs [{:keys [data in-progress? show-password-input?]} [:wallet.send/transaction]]
    [react/view {:flex 1
                 :flex-direction :row}
     [react/view styles/send-transaction-form-container
      [status-bar/status-bar {:type :modal-wallet}]
      [toolbar true (i18n/label :t/sign-message)]
      [react/view components.styles/flex
       [react/scroll-view
        [react/view styles/send-transaction-form
         [wallet.components/cartouche {:disabled? true}
          (i18n/label :t/message)
          [components/amount-input
           {:disabled?     true
            :input-options {:multiline true}
            :amount-text   data}
           nil]]]]
       [modal-enter-password-buttons false
        #(re-frame/dispatch [:wallet/discard-transaction-navigate-back])
        #(re-frame/dispatch [:set-in
                             [:wallet :send-transaction :show-password-input?] true])
        #_(re-frame/dispatch [:wallet/sign-message])
        :t/transactions-sign]]]
     (when show-password-input?
       [opacify-background])
     (when show-password-input?
       [password-input-panel {:message data} false])
     (when in-progress?
       [react/view styles/processing-view])]))
